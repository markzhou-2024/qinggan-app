INSERT INTO trip (id, code, name, start_date, end_date, duration_days, status, revision, created_at, updated_at)
VALUES (1, 'qinggan-2026-family', '青甘大环线10天自驾', DATE '2026-08-13', DATE '2026-08-22', 10, 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO place (id, name, place_type, province, city, priority, recommended_duration_minutes, description) VALUES
 (1, '南京', 'CITY', '江苏', '南京', NULL, NULL, '旅行起点与最终目的地'),
 (2, '洛阳', 'CITY', '河南', '洛阳', NULL, NULL, '长途驾驶过夜城市'),
 (3, '西宁', 'CITY', '青海', '西宁', NULL, NULL, '青甘环线起点城市'),
 (4, '青海湖', 'SCENIC', '青海', '海南藏族自治州', 'S', 180, '核心必去景点'),
 (5, '茶卡镇', 'OVERNIGHT', '青海', '海西蒙古族藏族自治州', NULL, NULL, '茶卡住宿地'),
 (6, '茶卡天空壹号', 'SCENIC', '青海', '海西蒙古族藏族自治州', 'A_PLUS', 150, '强烈保留景点'),
 (7, '大柴旦翡翠湖', 'SCENIC', '青海', '海西蒙古族藏族自治州', 'S', 120, '核心必去景点'),
 (8, '大柴旦镇', 'OVERNIGHT', '青海', '海西蒙古族藏族自治州', NULL, NULL, '大柴旦住宿地'),
 (9, 'U型公路', 'SCENIC', '青海', '海西蒙古族藏族自治州', 'B', 30, '可选拍照节点'),
 (10, '水上雅丹', 'SCENIC', '青海', '海西蒙古族藏族自治州', 'A', 180, '推荐保留景点'),
 (11, '敦煌', 'CITY', '甘肃', '敦煌', NULL, NULL, '敦煌住宿地'),
 (12, '鸣沙山月牙泉', 'SCENIC', '甘肃', '敦煌', 'S', 180, '核心必去景点'),
 (13, '莫高窟', 'SCENIC', '甘肃', '敦煌', 'S_PLUS', 240, '最高优先级景点'),
 (14, '嘉峪关', 'SCENIC', '甘肃', '嘉峪关', 'B', 120, '可选景点'),
 (15, '张掖七彩丹霞', 'SCENIC', '甘肃', '张掖', 'S', 180, '核心必去景点'),
 (16, '祁连山草原/G227', 'SCENIC', '青海', '海北藏族自治州', 'A_PLUS', 90, '强烈保留景观路线'),
 (17, '卓尔山', 'SCENIC', '青海', '海北藏族自治州', 'A', 120, '推荐保留景点'),
 (18, '祁连县', 'OVERNIGHT', '青海', '海北藏族自治州', NULL, NULL, '祁连住宿地'),
 (19, '门源', 'SCENIC', '青海', '海北藏族自治州', 'B', 60, '可选节点'),
 (20, '西宁方向', 'TRANSFER', '青海', '西宁', NULL, NULL, '返程中转方向'),
 (21, '兰州东部', 'OVERNIGHT', '甘肃', '兰州', NULL, NULL, '兰州住宿地'),
 (22, '兰州', 'CITY', '甘肃', '兰州', NULL, NULL, '返程出发城市'),
 (23, '张掖七彩丹霞景区附近', 'OVERNIGHT', '甘肃', '张掖', NULL, NULL, '张掖住宿地');

INSERT INTO trip_day (id, trip_id, day_number, date, title, day_type, planned_distance_km, planned_distance_display, planned_drive_minutes, planned_drive_display, overnight_place_id, sequence) VALUES
 (1, 1, 1, DATE '2026-08-13', '南京 → 洛阳', 'LONG_DRIVE', 748, '约748km', 510, '约8～8.5h', 2, 1),
 (2, 1, 2, DATE '2026-08-14', '洛阳 → 西宁', 'LONG_DRIVE', 1250, '约1250km', 900, '约14.5～15.5h', 3, 2),
 (3, 1, 3, DATE '2026-08-15', '西宁 → 青海湖 → 茶卡镇', 'TOURING', 300, '约300km', NULL, NULL, 5, 3),
 (4, 1, 4, DATE '2026-08-16', '茶卡镇 → 茶卡天空壹号 → 大柴旦翡翠湖 → 大柴旦镇', 'MIXED', 400, '约400km', NULL, NULL, 8, 4),
 (5, 1, 5, DATE '2026-08-17', '大柴旦 → 水上雅丹 → 敦煌 → 鸣沙山月牙泉', 'MIXED', 670, '660～680km', NULL, NULL, 11, 5),
 (6, 1, 6, DATE '2026-08-18', '敦煌 → 莫高窟 → 张掖七彩丹霞景区附近', 'MIXED', 594, '约594km', NULL, NULL, 23, 6),
 (7, 1, 7, DATE '2026-08-19', '张掖七彩丹霞 → 祁连山草原/G227 → 卓尔山 → 祁连县', 'TOURING', 230, '约210～250km', NULL, NULL, 18, 7),
 (8, 1, 8, DATE '2026-08-20', '祁连 → 门源 → 西宁方向 → 兰州', 'RETURN', 495, '约495km', NULL, NULL, 21, 8),
 (9, 1, 9, DATE '2026-08-21', '兰州 → 洛阳', 'RETURN', 1000, '约1000km', NULL, NULL, 2, 9),
 (10, 1, 10, DATE '2026-08-22', '洛阳 → 南京', 'RETURN', 748, '约748km', NULL, NULL, 1, 10);

INSERT INTO trip_stop (id, trip_day_id, place_id, sequence, stop_type, priority, optional, status) VALUES
 (1,1,1,1,'ORIGIN',NULL,FALSE,'PLANNED'), (2,1,2,2,'OVERNIGHT',NULL,FALSE,'PLANNED'),
 (3,2,2,1,'ORIGIN',NULL,FALSE,'PLANNED'), (4,2,3,2,'OVERNIGHT',NULL,FALSE,'PLANNED'),
 (5,3,3,1,'ORIGIN',NULL,FALSE,'PLANNED'), (6,3,4,2,'SCENIC','S',FALSE,'PLANNED'), (7,3,5,3,'OVERNIGHT',NULL,FALSE,'PLANNED'),
 (8,4,5,1,'ORIGIN',NULL,FALSE,'PLANNED'), (9,4,6,2,'SCENIC','A_PLUS',FALSE,'PLANNED'), (10,4,7,3,'SCENIC','S',FALSE,'PLANNED'), (11,4,8,4,'OVERNIGHT',NULL,FALSE,'PLANNED'),
 (12,5,8,1,'ORIGIN',NULL,FALSE,'PLANNED'), (13,5,9,2,'SCENIC','B',TRUE,'PLANNED'), (14,5,10,3,'SCENIC','A',FALSE,'PLANNED'), (15,5,11,4,'TRANSFER',NULL,FALSE,'PLANNED'), (16,5,12,5,'SCENIC','S',FALSE,'PLANNED'),
 (17,6,11,1,'ORIGIN',NULL,FALSE,'PLANNED'), (18,6,13,2,'SCENIC','S_PLUS',FALSE,'PLANNED'), (19,6,14,3,'SCENIC','B',TRUE,'PLANNED'), (20,6,23,4,'OVERNIGHT',NULL,FALSE,'PLANNED'),
 (21,7,15,1,'ORIGIN','S',FALSE,'PLANNED'), (22,7,16,2,'SCENIC','A_PLUS',FALSE,'PLANNED'), (23,7,17,3,'SCENIC','A',FALSE,'PLANNED'), (24,7,18,4,'OVERNIGHT',NULL,FALSE,'PLANNED'),
 (25,8,18,1,'ORIGIN',NULL,FALSE,'PLANNED'), (26,8,19,2,'SCENIC','B',TRUE,'PLANNED'), (27,8,20,3,'TRANSFER',NULL,FALSE,'PLANNED'), (28,8,21,4,'OVERNIGHT',NULL,FALSE,'PLANNED'),
 (29,9,22,1,'ORIGIN',NULL,FALSE,'PLANNED'), (30,9,2,2,'OVERNIGHT',NULL,FALSE,'PLANNED'),
 (31,10,2,1,'ORIGIN',NULL,FALSE,'PLANNED'), (32,10,1,2,'DESTINATION',NULL,FALSE,'PLANNED');

INSERT INTO navigation_point (id, place_id, name, address, navigation_type, navigation_keyword, recommended, warning_text, verification_status) VALUES
 (1,4,'青海湖景区游客中心',NULL,'ENTRANCE','青海湖景区游客中心',TRUE,'推荐导航点待出发前核验','PENDING'),
 (2,6,'茶卡天空壹号停车场',NULL,'PARKING','茶卡天空壹号停车场',TRUE,'推荐导航点待出发前核验','PENDING'),
 (3,7,'翡翠湖景区停车场',NULL,'PARKING','大柴旦翡翠湖景区停车场',TRUE,'推荐导航点待出发前核验','PENDING'),
 (4,10,'水上雅丹景区停车场',NULL,'PARKING','水上雅丹景区停车场',TRUE,'推荐导航点待出发前核验','PENDING'),
 (5,12,'鸣沙山月牙泉游客中心',NULL,'ENTRANCE','鸣沙山月牙泉游客中心',TRUE,'推荐导航点待出发前核验','PENDING'),
 (6,13,'莫高窟数字展示中心',NULL,'ENTRANCE','莫高窟数字展示中心',TRUE,'推荐导航点待出发前核验','PENDING'),
 (7,15,'张掖七彩丹霞北门停车场',NULL,'PARKING','张掖七彩丹霞北门停车场',TRUE,'推荐导航点待出发前核验','PENDING'),
 (8,17,'卓尔山景区停车场',NULL,'PARKING','卓尔山景区停车场',TRUE,'推荐导航点待出发前核验','PENDING');

INSERT INTO stay (id, trip_day_id, hotel_name, address, phone, check_in_note, parking_note, verification_status) VALUES
 (1,1,'洛阳住宿（待确认）','洛阳','',NULL,NULL,'PENDING'), (2,2,'西宁住宿（待确认）','西宁','',NULL,NULL,'PENDING'),
 (3,3,'茶卡镇住宿（待确认）','茶卡镇','',NULL,NULL,'PENDING'), (4,4,'大柴旦镇住宿（待确认）','大柴旦镇','',NULL,NULL,'PENDING'),
 (5,5,'敦煌住宿（待确认）','敦煌','',NULL,NULL,'PENDING'), (6,6,'张掖七彩丹霞景区附近住宿（待确认）','张掖','',NULL,NULL,'PENDING'),
 (7,7,'祁连县住宿（待确认）','祁连县','',NULL,NULL,'PENDING'), (8,8,'兰州东部住宿（待确认）','兰州东部','',NULL,NULL,'PENDING'),
 (9,9,'洛阳住宿（待确认）','洛阳','',NULL,NULL,'PENDING'), (10,10,'南京（旅行结束）','南京','',NULL,NULL,'PENDING');

INSERT INTO trip_progress (id, trip_id, current_day_number, current_stop_id, last_completed_stop_id, state, revision, updated_at)
VALUES (1, 1, 1, NULL, NULL, 'NOT_STARTED', 1, CURRENT_TIMESTAMP);
