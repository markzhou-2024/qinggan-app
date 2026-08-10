import SwiftUI
import UIKit
import QingGanCore

struct TodayView: View {
    @State private var viewModel: TodayViewModel
    init(viewModel: TodayViewModel) { _viewModel = State(initialValue: viewModel) }

    var body: some View {
        NavigationStack {
            Group {
                switch viewModel.state {
                case .loading: ProgressView("正在读取行程")
                case .unavailable(let message): ContentUnavailableView("今天暂无行程", systemImage: "calendar.badge.exclamationmark", description: Text(message))
                case .ready(let snapshot):
                    ScrollView { VStack(alignment: .leading, spacing: 20) {
                        DayHeaderSection(snapshot: snapshot)
                        JourneyProgressSection(stops: snapshot.day.stops)
                        NextStopSection(snapshot: snapshot)
                        DrivingStatusSection(day: snapshot.day)
                        WeatherSection()
                        TonightSection(stay: snapshot.tonightStay)
                        DailyMomentSection()
                    }.padding() }
                }
            }
            .navigationTitle("青甘随行")
            .task { await viewModel.load(now: AppDateProvider().now()) }
        }
    }
}

private struct DayHeaderSection: View {
    let snapshot: TodaySnapshot
    var body: some View { VStack(alignment: .leading, spacing: 6) {
        Text("青甘 · Day \(snapshot.day.number)").font(.largeTitle.bold())
        Text(snapshot.day.date.formatted(.dateTime.year().month().day())).foregroundStyle(.secondary)
        Text("\(snapshot.day.origin.name) → \(snapshot.day.destination.name)").font(.title3.weight(.semibold))
    }}
}

private struct JourneyProgressSection: View {
    let stops: [TripStop]
    var body: some View { Group { if !stops.isEmpty { VStack(alignment: .leading, spacing: 10) {
        Text("今日旅程").font(.headline)
        ForEach(stops.sorted { $0.sequence < $1.sequence }) { stop in
            Label(stop.place.name, systemImage: stop.status == .completed ? "checkmark.circle.fill" : "circle")
                .foregroundStyle(stop.status == .completed ? .green : .primary)
        }
    }.travelCard() } } }
}

private struct NextStopSection: View {
    let snapshot: TodaySnapshot
    var body: some View { VStack(alignment: .leading, spacing: 12) {
        Text("NEXT").font(.caption.weight(.bold)).foregroundStyle(.orange)
        Text(snapshot.nextStop?.place.name ?? "今日节点已完成").font(.title2.bold())
        if let point = snapshot.recommendedNavigationPoint {
            Text("推荐导航：\(point.name)")
            if point.verificationStatus == .pending { Text("导航点待出发前核验，将使用名称导航。").font(.footnote).foregroundStyle(.secondary) }
            NavigationActions(point: point)
        } else { Text("该节点暂无已确认的推荐导航点。").foregroundStyle(.secondary) }
    }.travelCard(highlighted: true) }
}

private struct DrivingStatusSection: View {
    let day: TripDay
    var body: some View { Group { if let distance = day.plannedDistance { VStack(alignment: .leading, spacing: 6) {
        Text("驾驶进度").font(.headline); Text("今日计划 \(distance)")
        Text("实际里程和预计到达时间将在行程进度能力接入后显示。").font(.footnote).foregroundStyle(.secondary)
    }.travelCard() } } }
}

private struct TonightSection: View {
    let stay: Stay?
    var body: some View { Group { if let stay { VStack(alignment: .leading, spacing: 6) {
        Text("TONIGHT").font(.caption.weight(.bold)).foregroundStyle(.orange)
        Text(stay.hotelName).font(.title3.bold())
        if let address = stay.address { Text(address).foregroundStyle(.secondary) }
        if stay.verificationStatus == .pending { Text("住宿信息待出发前确认").font(.footnote).foregroundStyle(.secondary) }
    }.travelCard() } } }
}

/// Intentionally empty until the weather contract carries travel advice; preserves the composition boundary.
private struct WeatherSection: View { var body: some View { EmptyView() } }
/// Reserved for future family task / photography moment without coupling it to itinerary rendering.
private struct DailyMomentSection: View { var body: some View { EmptyView() } }

private struct NavigationActions: View {
    let point: NavigationPoint
    private let service = MapURLNavigationService(canOpenURL: { UIApplication.shared.canOpenURL($0) })
    var body: some View { Menu {
        ForEach(service.availableProviders(for: point), id: \.rawValue) { provider in
            if let url = try? service.navigationURL(for: point, provider: provider) { Link(provider.title, destination: url) }
        }
    } label: { Label("开始导航", systemImage: "location.fill").frame(maxWidth: .infinity).padding(.vertical, 12) }
    .buttonStyle(.borderedProminent) }
}

private extension NavigationProvider { var title: String { switch self { case .appleMaps: "Apple 地图"; case .amap: "高德地图"; case .baiduMaps: "百度地图" } } }
private extension View { func travelCard(highlighted: Bool = false) -> some View { padding().frame(maxWidth: .infinity, alignment: .leading).background(highlighted ? Color.orange.opacity(0.12) : Color(uiColor: .secondarySystemBackground)).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous)) } }
