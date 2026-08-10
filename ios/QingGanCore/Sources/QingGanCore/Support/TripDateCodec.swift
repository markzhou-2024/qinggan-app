import Foundation

public enum TripDateCodec {
    public static let day: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    public static func timestampDate(from value: String) -> Date? {
        ISO8601DateFormatter().date(from: value)
    }

    public static func timestampString(from value: Date) -> String {
        ISO8601DateFormatter().string(from: value)
    }
}
