import Foundation

struct ExampleDataset: Identifiable {
    let id: String
    let name: String
    let csvContent: String
    let idColumn: String
    let timestampColumn: String
    let targetColumn: String
    var defaultPredictionLength: String = "24"
    var defaultQuantiles: String = "0.1, 0.5, 0.9"
}

struct ExampleData {
    static let airPassengers = ExampleDataset(
        id: "air_passengers",
        name: "Air Passengers (Monthly)",
        csvContent: """
item_id,Month,#Passengers
Air,1949-01-01,112
Air,1949-02-01,118
Air,1949-03-01,132
Air,1949-04-01,129
Air,1949-05-01,121
Air,1949-06-01,135
Air,1949-07-01,148
Air,1949-08-01,148
Air,1949-09-01,136
Air,1949-10-01,119
Air,1949-11-01,104
Air,1949-12-01,118
""",
        idColumn: "item_id",
        timestampColumn: "Month",
        targetColumn: "#Passengers",
        defaultPredictionLength: "24",
        defaultQuantiles: "0.1, 0.5, 0.9"
    )

    static let lateNightSnacks = ExampleDataset(
        id: "user_1",
        name: "Personal Finance: Late Night Snacks",
        csvContent: """
date,late_night_snack_count,daily_spend_usd
2025-12-20,1,38.47
2025-12-21,2,48.97
2025-12-22,1,32.13
2025-12-23,1,30.36
2025-12-24,1,12.89
2025-12-25,1,25.65
2025-12-26,1,8.64
2025-12-27,3,40.33
2025-12-28,0,27.38
2025-12-29,0,13.77
2025-12-30,0,19.18
2025-12-31,0,15.64
2026-01-01,1,21.79
2026-01-02,0,6.04
2026-01-03,1,16.5
2026-01-04,0,30.5
2026-01-05,3,26.88
2026-01-06,0,17.41
2026-01-07,3,22.62
2026-01-08,0,15.85
2026-01-09,3,25.26
""",
        idColumn: "id",
        timestampColumn: "date",
        targetColumn: "daily_spend_usd",
        defaultPredictionLength: "5",
        defaultQuantiles: "0.5, 0.9"
    )

    static let cloudCallEvents = ExampleDataset(
        id: "server_cluster_a",
        name: "Server Ops: API Spike Detection",
        csvContent: """
date,daily_app_events_count
2025-12-01,184
2025-12-02,185
2025-12-03,187
2025-12-04,185
2025-12-05,197
2025-12-06,156
2025-12-07,152
2025-12-08,194
2025-12-09,202
2025-12-10,207
2025-12-11,200
2025-12-12,176
2025-12-13,193
2025-12-14,177
2025-12-15,195
2025-12-16,190
2025-12-17,217
2025-12-18,205
2025-12-19,215
2025-12-20,181
2025-12-21,183
2025-12-22,185
2025-12-23,248
2025-12-24,227
2025-12-25,212
2025-12-26,201
2025-12-27,198
2025-12-28,169
2025-12-29,212
2025-12-30,223
""",
        idColumn: "id",
        timestampColumn: "date",
        targetColumn: "daily_app_events_count",
        defaultPredictionLength: "7",
        defaultQuantiles: "0.8, 0.9, 0.95, 0.99"
    )

    static let signalStrength = ExampleDataset(
        id: "elevator_b2",
        name: "Offline AI: Signal Strength",
        csvContent: """
timestamp,slot,condition_score_0_10
2026-01-01T07:00,morning,5.78
2026-01-01T13:00,lunch,5.48
2026-01-01T22:00,night,6.25
2026-01-02T07:00,morning,7.1
2026-01-02T13:00,lunch,5.87
2026-01-02T22:00,night,5.96
2026-01-03T07:00,morning,7.55
2026-01-03T13:00,lunch,7.78
2026-01-03T22:00,night,7.66
2026-01-04T07:00,morning,6.31
2026-01-04T13:00,lunch,7.85
2026-01-04T22:00,night,7.49
2026-01-05T07:00,morning,5.12
2026-01-05T13:00,lunch,6.26
2026-01-05T22:00,night,5.56
2026-01-06T07:00,morning,3.91
2026-01-06T13:00,lunch,5.15
2026-01-06T22:00,night,4.05
2026-01-07T07:00,morning,5.19
2026-01-07T13:00,lunch,5.13
2026-01-07T22:00,night,4.75
2026-01-08T07:00,morning,5.19
2026-01-08T13:00,lunch,6.83
2026-01-08T22:00,night,5.71
2026-01-09T07:00,morning,6.28
2026-01-09T13:00,lunch,6.83
2026-01-09T22:00,night,7.06
2026-01-10T07:00,morning,7.69
2026-01-10T13:00,lunch,8.22
2026-01-10T22:00,night,7.93
2026-01-11T07:00,morning,8.11
2026-01-11T13:00,lunch,7.08
2026-01-11T22:00,night,6.71
2026-01-12T07:00,morning,4.59
2026-01-12T13:00,lunch,5.95
2026-01-12T22:00,night,5.96
2026-01-13T07:00,morning,4.36
2026-01-13T13:00,lunch,4.43
2026-01-13T22:00,night,4.14
2026-01-14T07:00,morning,5.05
2026-01-14T13:00,lunch,5.61
2026-01-14T22:00,night,5.19
""",
        idColumn: "id",
        timestampColumn: "timestamp",

        targetColumn: "condition_score_0_10",
        defaultPredictionLength: "48",
        defaultQuantiles: "0.1, 0.5, 0.9"
    )

    static let hourlyDemand = ExampleDataset(
        id: "store_gangnam",
        name: "Retail Analytics: Hourly Demand",
        csvContent: """
timestamp,hourly_signal_value
2026-01-10T00:00,12.87
2026-01-10T01:00,10.16
2026-01-10T02:00,12.0
2026-01-10T03:00,7.75
2026-01-10T04:00,15.73
2026-01-10T05:00,19.97
2026-01-10T06:00,26.77
2026-01-10T07:00,34.24
2026-01-10T08:00,38.15
2026-01-10T09:00,38.19
2026-01-10T10:00,26.58
2026-01-10T11:00,22.06
2026-01-10T12:00,15.29
2026-01-10T13:00,13.01
2026-01-10T14:00,16.41
2026-01-10T15:00,18.89
2026-01-10T16:00,27.16
2026-01-10T17:00,32.84
2026-01-10T18:00,41.35
2026-01-10T19:00,50.04
2026-01-10T20:00,53.99
2026-01-10T21:00,50.92
2026-01-10T22:00,40.49
2026-01-10T23:00,33.37
2026-01-11T00:00,10.28
2026-01-11T01:00,15.5
2026-01-11T02:00,14.72
2026-01-11T03:00,10.38
2026-01-11T04:00,15.2
2026-01-11T05:00,18.61
2026-01-11T06:00,28.15
2026-01-11T07:00,36.86
2026-01-11T08:00,41.53
2026-01-11T09:00,34.57
2026-01-11T10:00,28.05
2026-01-11T11:00,17.26
2026-01-11T12:00,15.99
2026-01-11T13:00,11.39
2026-01-11T14:00,13.04
2026-01-11T15:00,15.78
2026-01-11T16:00,24.01
2026-01-11T17:00,29.32
2026-01-11T18:00,39.65
2026-01-11T19:00,55.22
2026-01-11T20:00,50.63
2026-01-11T21:00,48.12
2026-01-11T22:00,46.92
2026-01-11T23:00,39.56
""",
        idColumn: "id",
        timestampColumn: "timestamp",
        targetColumn: "hourly_signal_value",
        defaultPredictionLength: "24",
        defaultQuantiles: "0.1, 0.5, 0.9"
    )

    static let sleepTracker = ExampleDataset(
        id: "user_health",
        name: "Health Privacy: Sleep Tracker",
        csvContent: """
date,steps,sleep_hours
2025-12-15,6984,6.64
2025-12-16,8492,7.33
2025-12-17,8886,7.02
2025-12-18,7231,5.92
2025-12-19,5353,6.46
2025-12-20,2211,6.76
2025-12-21,8059,8.15
2025-12-22,7572,6.65
2025-12-23,8535,6.56
2025-12-24,8014,6.46
2025-12-25,7948,6.18
2025-12-26,5085,6.07
2025-12-27,7287,7.73
2025-12-28,8189,7.27
2025-12-29,6331,6.75
2025-12-30,7555,7.14
2025-12-31,9403,6.65
2026-01-01,7770,6.01
2026-01-02,6183,5.81
2026-01-03,7190,7.66
2026-01-04,7198,7.55
2026-01-05,7397,6.86
2026-01-06,8680,7.39
2026-01-07,7462,6.03
2026-01-08,9358,6.18
2026-01-09,7907,5.85
2026-01-10,7614,6.97
2026-01-11,7158,7.57
""",
        idColumn: "id",
        timestampColumn: "date",
        targetColumn: "sleep_hours",
        defaultPredictionLength: "7",
        defaultQuantiles: "0.1, 0.5"
    )

    static let nasdaqYearly = ExampleDataset(
        id: "nasdaq_12m",
        name: "NASDAQ: Yearly (12M)",
        csvContent: """
time (UNIX),close
1420209000,5007.41
1451917800,5383.12
1483453800,6903.39
1514903400,6635.28
1546439400,8972.6
1577975400,12888.2824
1609770600,15644.9707
1641220200,10466.4817
1672756200,15011.3524
1704205800,19310.7916
1735828200,23241.9906
1767364200,23817.0981
""",
        idColumn: "id",
        timestampColumn: "time (UNIX)",
        targetColumn: "close",
        defaultPredictionLength: "10",
        defaultQuantiles: "0.1, 0.5, 0.9"
    )

    static let nasdaqHalfYearly = ExampleDataset(
        id: "nasdaq_6m",
        name: "NASDAQ: Half-Yearly (6M)",
        csvContent: """
time (UNIX),close
1136298600,2172.09
1151933400,2415.29
1167834600,2603.23
1183383000,2652.28
1199284200,2292.98
1214919000,1577.03
1230906600,1835.04
1246455000,2269.15
1262615400,2109.24
1277991000,2652.87
1294065000,2773.52
1309527000,2605.15
1325601000,2935.05
1341235800,3019.51
1357137000,3403.25
1372685400,4176.59
1388673000,4408.18
1404221400,4736.05
1420209000,4986.87
1435757400,5007.41
1451917800,4842.67
1467379800,5383.12
1483453800,6140.42
1499088600,6903.39
1514903400,7510.3
1530538200,6635.28
1546439400,8006.24
1561987800,8972.6
1577975400,10058.77
1593610200,12888.2824
1609770600,14503.9534
1625146200,15644.9707
1641220200,11028.7357
1656682200,10466.4817
1672756200,13787.9227
1688391000,15011.3524
1704205800,17732.6027
1719840600,19310.7916
1735828200,20369.7334
1751376600,23241.9906
1767364200,23817.0981
""",
        idColumn: "id",
        timestampColumn: "time (UNIX)",
        targetColumn: "close",
        defaultPredictionLength: "10",
        defaultQuantiles: "0.1, 0.5, 0.9"
    )

    static let allValues = [
        nasdaqYearly,
        nasdaqHalfYearly,
        lateNightSnacks,
        cloudCallEvents,
        signalStrength,
        hourlyDemand,
        sleepTracker
    ]
}
