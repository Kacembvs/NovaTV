import Foundation
import Network
import SwiftData
import UIKit

/// Offline movies and episodes: background URLSession downloads that survive the app being closed,
/// can be paused/resumed, and are played from local storage without any network.
@MainActor
final class DownloadCenter: NSObject, ObservableObject {
    nonisolated static let maxParallel = 2

    @Published private(set) var revision = 0
    private let container: ModelContainer
    private let settings: AppSettings
    private var context: ModelContext { container.mainContext }
    private var tasks: [String: URLSessionDownloadTask] = [:]
    private var lastProgressSave: [String: Date] = [:]
    /// Called by the system when background downloads finish while the app was suspended.
    var backgroundCompletion: (() -> Void)?

    private lazy var wifiSession = makeSession(id: "com.nova.tv.downloads.wifi", cellular: false)
    private lazy var anySession = makeSession(id: "com.nova.tv.downloads.any", cellular: true)

    init(container: ModelContainer, settings: AppSettings) {
        self.container = container
        self.settings = settings
        super.init()
        monitor.pathUpdateHandler = { [weak self] path in
            let constrained = path.status != .satisfied || path.isExpensive
            Task { @MainActor in self?.onCellularOrOffline = constrained }
        }
        monitor.start(queue: DispatchQueue(label: "com.nova.tv.network"))
    }

    private let monitor = NWPathMonitor()
    @Published private(set) var onCellularOrOffline = false
    /// Queued downloads are waiting because only Wi-Fi is allowed.
    var isWaitingForWifi: Bool { settings.downloadWifiOnly && onCellularOrOffline }

    private func makeSession(id: String, cellular: Bool) -> URLSession {
        let config = URLSessionConfiguration.background(withIdentifier: id)
        config.allowsCellularAccess = cellular
        config.allowsExpensiveNetworkAccess = cellular
        config.isDiscretionary = false
        config.sessionSendsLaunchEvents = true
        config.httpMaximumConnectionsPerHost = Self.maxParallel
        return URLSession(configuration: config, delegate: self, delegateQueue: .main)
    }

    nonisolated static var folder: URL {
        let url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Offline", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var mutable = url
        try? mutable.setResourceValues(values)
        return url
    }

    nonisolated private static func resumeFile(_ key: String) -> URL {
        folder.appendingPathComponent(safeName(key) + ".resume")
    }

    nonisolated static func safeName(_ key: String) -> String {
        String(key.map { $0.isLetter || $0.isNumber || $0 == "-" ? $0 : "_" })
    }

    func localURL(_ record: DownloadRecord) -> URL? {
        guard record.state == .completed, let name = record.fileName else { return nil }
        let url = Self.folder.appendingPathComponent(name)
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }

    /// Reconnects to downloads started in a previous launch.
    func restore() {
        for session in [wifiSession, anySession] {
            session.getAllTasks { [weak self] tasks in
                Task { @MainActor in
                    for case let task as URLSessionDownloadTask in tasks {
                        if let key = task.taskDescription { self?.tasks[key] = task }
                    }
                    self?.startQueued()
                }
            }
        }
    }

    func record(_ profileID: UUID, _ ref: String) -> DownloadRecord? {
        let key = "\(profileID.uuidString)|\(ref)"
        return try? context.fetch(FetchDescriptor<DownloadRecord>(predicate: #Predicate<DownloadRecord> { $0.key == key })).first
    }

    func records(state: DownloadState) -> [DownloadRecord] {
        let raw = state.rawValue
        return (try? context.fetch(FetchDescriptor<DownloadRecord>(predicate: #Predicate<DownloadRecord> { $0.stateRaw == raw },
                                                                 sortBy: [SortDescriptor(\.createdAt)]))) ?? []
    }

    /// Queues items (a movie, an episode or a whole season).
    func download(_ items: [(item: PlayableItem, season: Int?, episode: Int?)], profile: Profile) throws {
        for entry in items {
            guard entry.item.kind != .live else { throw NovaError("Les chaînes en direct ne peuvent pas être téléchargées.") }
            if let existing = record(profile.id, entry.item.ref) {
                if existing.state == .failed { existing.state = .queued; existing.failure = nil }
                continue
            }
            guard let url = StreamURLs.url(for: entry.item.ref, profile: profile) else { continue }
            if url.pathExtension.lowercased() == "m3u8" {
                throw NovaError("Ce titre est diffusé en flux adaptatif (HLS) et ne peut pas être téléchargé.")
            }
            context.insert(DownloadRecord(profileID: profile.id, item: entry.item, season: entry.season, episode: entry.episode))
        }
        try context.save()
        startQueued()
        revision += 1
    }

    /// Keeps at most two transfers running at once.
    func startQueued() {
        let running = records(state: .downloading).count
        guard running < Self.maxParallel else { return }
        for record in records(state: .queued).prefix(Self.maxParallel - running) { start(record) }
        try? context.save()
        revision += 1
    }

    private func start(_ record: DownloadRecord) {
        guard let profile = context.profile(record.profileID), let url = StreamURLs.url(for: record.ref, profile: profile) else {
            record.state = .failed
            record.failure = "Profil introuvable."
            return
        }
        let session = settings.downloadWifiOnly ? wifiSession : anySession
        let task: URLSessionDownloadTask
        if let data = try? Data(contentsOf: Self.resumeFile(record.key)) {
            task = session.downloadTask(withResumeData: data)
            try? FileManager.default.removeItem(at: Self.resumeFile(record.key))
        } else {
            var request = URLRequest(url: url)
            request.setValue("NOVA-IPTV/1.0 (iOS)", forHTTPHeaderField: "User-Agent")
            task = session.downloadTask(with: request)
        }
        task.taskDescription = record.key
        tasks[record.key] = task
        record.state = .downloading
        task.resume()
    }

    func pause(_ record: DownloadRecord) {
        let key = record.key
        record.state = .paused
        try? context.save()
        revision += 1
        if let task = tasks[key] {
            task.cancel(byProducingResumeData: { data in
                if let data { try? data.write(to: Self.resumeFile(key)) }
            })
            tasks[key] = nil
        }
        startQueued()
    }

    func resume(_ record: DownloadRecord) {
        record.state = .queued
        record.failure = nil
        try? context.save()
        startQueued()
    }

    func delete(_ record: DownloadRecord) {
        let key = record.key
        tasks[key]?.cancel()
        tasks[key] = nil
        if let name = record.fileName { try? FileManager.default.removeItem(at: Self.folder.appendingPathComponent(name)) }
        try? FileManager.default.removeItem(at: Self.resumeFile(key))
        context.delete(record)
        try? context.save()
        revision += 1
        startQueued()
    }

    func deleteAll(profileID: UUID? = nil) {
        let all = (try? context.fetch(FetchDescriptor<DownloadRecord>())) ?? []
        for record in all where profileID == nil || record.profileID == profileID { delete(record) }
    }

    /// Netflix-style smart downloads: a watched episode makes room for the next one.
    func episodeFinished(profile: Profile, item: PlayableItem, next: PlayableItem?) {
        guard settings.smartDownloads, item.kind == .episode, let current = record(profile.id, item.ref), current.state == .completed else { return }
        delete(current)
        if let next, next.parentRef == item.parentRef, record(profile.id, next.ref) == nil {
            try? download([(next, nil, nil)], profile: profile)
        }
    }

    var usedBytes: Int64 {
        let files = (try? FileManager.default.contentsOfDirectory(at: Self.folder, includingPropertiesForKeys: [.fileSizeKey])) ?? []
        return files.reduce(0) { $0 + Int64((try? $1.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0) }
    }

    var freeBytes: Int64 {
        let values = try? URL(fileURLWithPath: NSHomeDirectory()).resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey])
        return values?.volumeAvailableCapacityForImportantUsage ?? 0
    }

    fileprivate func recordFor(_ task: URLSessionTask) -> DownloadRecord? {
        guard let key = task.taskDescription else { return nil }
        return try? context.fetch(FetchDescriptor<DownloadRecord>(predicate: #Predicate<DownloadRecord> { $0.key == key })).first
    }

    fileprivate func finished(_ task: URLSessionDownloadTask, file: URL) {
        guard let record = recordFor(task) else { return }
        let response = task.response as? HTTPURLResponse
        if let status = response?.statusCode, !(200..<300).contains(status) {
            record.state = .failed
            record.failure = "Le serveur a répondu HTTP \(status)."
            return
        }
        let ext = task.originalRequest?.url?.pathExtension.lowercased()
        let name = Self.safeName(record.key) + "." + ((ext?.isEmpty == false ? ext : nil) ?? "mp4")
        let destination = Self.folder.appendingPathComponent(name)
        try? FileManager.default.removeItem(at: destination)
        do {
            try FileManager.default.moveItem(at: file, to: destination)
            record.fileName = name
            record.bytes = Int64((try? destination.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)
            record.progress = 1
            record.state = .completed
        } catch {
            record.state = .failed
            record.failure = "Espace de stockage insuffisant ou fichier inaccessible."
        }
    }
}

extension DownloadCenter: URLSessionDownloadDelegate {
    nonisolated func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        // The temporary file is deleted when this method returns: move it synchronously first.
        let staging = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try? FileManager.default.moveItem(at: location, to: staging)
        MainActor.assumeIsolated {
            finished(downloadTask, file: staging)
            try? context.save()
            revision += 1
        }
    }

    nonisolated func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64,
                                totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        MainActor.assumeIsolated {
            guard let record = recordFor(downloadTask) else { return }
            record.bytes = totalBytesWritten
            if totalBytesExpectedToWrite > 0 {
                record.expectedBytes = totalBytesExpectedToWrite
                record.progress = Double(totalBytesWritten) / Double(totalBytesExpectedToWrite)
            }
            if record.state != .downloading { record.state = .downloading }
            let now = Date()
            if now.timeIntervalSince(lastProgressSave[record.key] ?? .distantPast) > 1 {
                lastProgressSave[record.key] = now
                try? context.save()
                revision += 1
            }
        }
    }

    nonisolated func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        MainActor.assumeIsolated {
            if let key = task.taskDescription { tasks[key] = nil }
            if let error, let record = recordFor(task), record.state != .paused, record.state != .completed {
                let ns = error as NSError
                if let data = ns.userInfo[NSURLSessionDownloadTaskResumeData] as? Data {
                    try? data.write(to: Self.resumeFile(record.key))
                }
                if ns.code != NSURLErrorCancelled {
                    record.state = .failed
                    record.failure = error.userMessage
                }
            }
            try? context.save()
            revision += 1
            startQueued()
        }
    }

    nonisolated func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        MainActor.assumeIsolated {
            backgroundCompletion?()
            backgroundCompletion = nil
        }
    }
}
