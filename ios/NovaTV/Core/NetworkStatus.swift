import Foundation
import Network

/// Live connectivity, so the app can point to downloads as soon as the connection drops.
final class NetworkStatus: ObservableObject {
    static let shared = NetworkStatus()

    @Published private(set) var online = true
    private let monitor = NWPathMonitor()

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let satisfied = path.status == .satisfied
            DispatchQueue.main.async { self?.online = satisfied }
        }
        monitor.start(queue: DispatchQueue(label: "com.nova.tv.reachability"))
    }
}
