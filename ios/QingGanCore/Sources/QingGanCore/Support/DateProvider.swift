import Foundation

public protocol DateProvider: Sendable {
    func now() -> Date
}

public struct SystemDateProvider: DateProvider, Sendable {
    public init() {}

    public func now() -> Date { Date() }
}

public struct FixedDateProvider: DateProvider, Sendable {
    private let date: Date

    public init(date: Date) {
        self.date = date
    }

    public func now() -> Date { date }
}
