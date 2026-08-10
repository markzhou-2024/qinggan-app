import Foundation
import QingGanCore

actor FileTripStore: LocalTripStore {
    private let fileURL: URL

    init(fileManager: FileManager = .default) {
        let directory = try? fileManager.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        fileURL = (directory ?? fileManager.temporaryDirectory).appending(path: "qinggan-itinerary-cache.json")
    }

    func save(data: Data) async throws {
        try data.write(to: fileURL, options: .atomic)
    }

    func load() async throws -> Data? {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return nil }
        return try Data(contentsOf: fileURL)
    }
}
