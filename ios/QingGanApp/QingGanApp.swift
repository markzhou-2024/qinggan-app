import SwiftUI

@main
struct QingGanApp: App {
    var body: some Scene {
        WindowGroup {
            TodayView(
                viewModel: TodayViewModel(
                    repository: AppTripRepositoryFactory.make()
                )
            )
        }
    }
}
