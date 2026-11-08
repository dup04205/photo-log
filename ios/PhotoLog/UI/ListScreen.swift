import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

struct RootView: View {
    @EnvironmentObject var model: AppModel
    @State private var demoShare: DemoShare?

    var body: some View {
        NavigationStack(path: $model.path) {
            ListScreen()
                .navigationDestination(for: String.self) { id in DetailScreen(id: id) }
        }
        .tint(Palette.onSurface)
        .toastHost()
        .fullScreenCover(item: $demoShare) { d in
            ShareEditor(entry: d.entry, note: d.entry.note, initialRatio: d.ratio, initialStyle: d.style)
                .environmentObject(model)
        }
        .onAppear {
            #if targetEnvironment(macCatalyst)
            // 맥: 창을 너무 작게 줄이지 못하게
            for scene in UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }) {
                scene.sizeRestrictions?.minimumSize = CGSize(width: 720, height: 600)
            }
            #endif
            #if targetEnvironment(simulator)
            // 스크린샷 자동 촬영용(시뮬레이터 전용): -PLShare <id> -PLShareStyle card|overlay|photo -PLShareRatio 0~3
            let d = UserDefaults.standard
            if let id = d.string(forKey: "PLShare"), let e = model.entry(id) {
                let style = ShareStyle.allCases.first { "\($0)" == d.string(forKey: "PLShareStyle") } ?? .photo
                let ratio = d.object(forKey: "PLShareRatio") == nil ? 2 : d.integer(forKey: "PLShareRatio")
                demoShare = DemoShare(entry: e, ratio: ratio, style: style)
            }
            #endif
        }
        .task {
            // 처음 실행 시 권한: 위치(카메라 사진 보완용), 사진 보관함(가져온 사진의 촬영 위치·날짜)
            #if targetEnvironment(simulator)
            // 스크린샷 자동 촬영(-PLDemo) 중에는 권한 창을 띄우지 않는다
            if UserDefaults.standard.bool(forKey: "PLDemo") { return }
            #endif
            await MainActor.run {
                LocationProvider.shared.requestAccess()
                PhotoStore.requestLibraryAccess()
            }
        }
    }
}

enum ImportKind {
    case backup, photos
}

struct DemoShare: Identifiable {
    let entry: LogEntry
    let ratio: Int
    let style: ShareStyle
    var id: String { entry.id }
}

/* ② 시작 화면: 연·월·일 아코디언 + 사진 그리드 */
struct ListScreen: View {
    @EnvironmentObject var model: AppModel
    // 저장값(@AppStorage)은 바뀔 때 애니메이션이 안 붙는 경우가 있어, 화면 상태(@State)를 따로 두고 움직인다
    @AppStorage("showIndex") private var savedShowIndex = true
    @State private var showIndex = UserDefaults.standard.object(forKey: "showIndex") as? Bool ?? true
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var pickerOpen = false
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var cameraOpen = false
    @State private var importerOpen = false
    @State private var importKind: ImportKind = .backup
    @State private var shareTarget: ShareTarget?
    @State private var deleteFor: LogEntry?

    var body: some View {
        let groups = model.byDay
        VStack(spacing: 0) {
            if model.busy {
                ProgressView().progressViewStyle(.linear).tint(Palette.onSurface)
            }
            ScrollViewReader { proxy in
                HStack(spacing: 0) {
                    // 사이드바는 항상 화면에 두고 폭을 0 ↔ 원래 폭으로 부드럽게 바꾼다.
                    // 오른쪽 정렬로 잘라서, 열릴 때는 왼쪽에서 밀려 나오고 닫힐 때는 왼쪽으로 밀려 들어간다.
                    let sideW: CGFloat = sizeClass == .regular ? 160 : 112
                    HStack(spacing: 0) {
                        DateIndex(groups: groups) { prefix in
                            guard let g = groups.first(where: { $0.day.hasPrefix(prefix) }) else { return }
                            withAnimation { proxy.scrollTo("h-\(g.day)", anchor: .top) }
                        }
                        .frame(width: sideW)
                        .opacity(showIndex ? 1 : 0)
                        Rectangle().fill(Palette.outlineVariant).frame(width: 1)
                    }
                    .frame(width: showIndex ? sideW + 1 : 0, alignment: .trailing)
                    .clipped()
                    .allowsHitTesting(showIndex)
                    .accessibilityHidden(!showIndex)
                    if groups.isEmpty {
                        emptyState
                    } else {
                        grid(groups)
                    }
                }
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Palette.background, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button {
                    withAnimation(.spring(response: 0.42, dampingFraction: 0.9)) { showIndex.toggle() }
                    savedShowIndex = showIndex
                } label: {
                    Image(systemName: "line.3.horizontal")
                }
                .accessibilityLabel(showIndex ? "날짜 메뉴 숨기기" : "날짜 메뉴 보기")
            }
            ToolbarItem(placement: .principal) { Logo() }
            ToolbarItem(placement: .navigationBarTrailing) {
                Menu {
                    Button("백업 내보내기") { model.exportBackup() }
                    Button("백업 불러오기") {
                        importKind = .backup
                        importerOpen = true
                    }
                } label: {
                    Image(systemName: "ellipsis").rotationEffect(.degrees(90))
                }
                .accessibilityLabel("더보기")
            }
        }
        .overlay(alignment: .bottomTrailing) { addButton }
        .photosPicker(
            isPresented: $pickerOpen,
            selection: $pickerItems,
            maxSelectionCount: nil,
            matching: .images,
            preferredItemEncoding: .current,
            photoLibrary: .shared()
        )
        .onChange(of: pickerItems) { items in
            guard !items.isEmpty else { return }
            model.importItems(items)
            pickerItems = []
        }
        .fullScreenCover(isPresented: $cameraOpen) {
            CameraView(isPresented: $cameraOpen) { img, meta in
                model.capture(image: img, metadata: meta)
            }
        }
        .fileImporter(
            isPresented: $importerOpen,
            allowedContentTypes: importKind == .photos ? [.image] : [.zip],
            allowsMultipleSelection: importKind == .photos
        ) { result in
            guard case .success(let urls) = result else { return }
            if importKind == .photos {
                model.importFiles(urls)
            } else if let url = urls.first {
                model.importBackup(url)
            }
        }
        .modifier(ShareFlow(target: $shareTarget))
        .deleteAlert($deleteFor) { model.delete($0) }
    }

    private var emptyState: some View {
        Text("아직 사진이 없습니다.\n아래 ＋ 사진 추가로 첫 기록을 남겨 보세요.\n사진은 앱 안에만 저장되어 사진 앱에 보이지 않습니다.")
            .font(.system(size: 14))
            .lineSpacing(8)
            .multilineTextAlignment(.center)
            .foregroundColor(Palette.onSurfaceVariant)
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func grid(_ groups: [DayGroup]) -> some View {
        // iPhone: 3열(사이드바 숨기면 4열) / iPad: 화면 폭에 맞춰 한 칸 약 150pt로 자동
        let columns = sizeClass == .regular
            ? [GridItem(.adaptive(minimum: 150), spacing: 6)]
            : Array(repeating: GridItem(.flexible(), spacing: 4), count: showIndex ? 3 : 4)
        return ScrollView {
            LazyVGrid(columns: columns, alignment: .leading, spacing: 4) {
                ForEach(groups) { g in
                    Section {
                        ForEach(g.items) { e in
                            Tile(
                                entry: e,
                                onOpen: { model.path = [e.id] },
                                onShare: { shareTarget = ShareTarget(entry: e, note: e.note) },
                                onDelete: { deleteFor = e }
                            )
                        }
                    } header: {
                        Text(g.day.replacingOccurrences(of: "-", with: "."))
                            .font(AppFont.index(13))
                            .foregroundColor(Palette.onSurfaceVariant)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.leading, 2)
                            .padding(.top, 10)
                            .padding(.bottom, 2)
                            .id("h-\(g.day)")
                    }
                }
            }
            .padding(.horizontal, 8)
            .padding(.top, 4)
            .padding(.bottom, 96)
        }
    }

    private var addButton: some View {
        Menu {
            Button {
                openCamera()
            } label: {
                Label("카메라로 찍기", systemImage: "camera")
            }
            Button {
                pickerOpen = true
            } label: {
                Label("앨범에서 가져오기", systemImage: "photo.on.rectangle")
            }
            #if targetEnvironment(macCatalyst)
            Button {
                importKind = .photos
                importerOpen = true
            } label: {
                Label("파일에서 가져오기", systemImage: "folder")
            }
            #endif
        } label: {
            Label("사진 추가", systemImage: "plus")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(Palette.onPrimary)
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .background(Palette.primary, in: RoundedRectangle(cornerRadius: 16))
                .shadow(color: .black.opacity(0.18), radius: 6, y: 3)
        }
        .padding(.trailing, 16)
        .padding(.bottom, 16)
    }

    private func openCamera() {
        let canShoot = CameraController.available
        guard canShoot else {
            model.show("카메라를 사용할 수 없습니다")
            return
        }
        LocationProvider.shared.refresh()
        cameraOpen = true
    }
}

/* ③ 연 → 월 → 일 아코디언 메뉴 */
struct DateIndex: View {
    let groups: [DayGroup]
    let onJump: (String) -> Void
    @State private var expanded: [String: Bool] = [:]

    private struct MonthGroup {
        let key: String      // yyyy-MM
        let month: String    // MM
        var days: [DayGroup]
        var count: Int { days.reduce(0) { $0 + $1.items.count } }
    }

    private struct YearGroup {
        let year: String
        var months: [MonthGroup]
        var count: Int { months.reduce(0) { $0 + $1.count } }
    }

    private var years: [YearGroup] {
        var out: [YearGroup] = []
        for g in groups {
            let y = String(g.day.prefix(4))
            let key = String(g.day.prefix(7))
            let m = String(key.dropFirst(5))
            if out.last?.year != y { out.append(YearGroup(year: y, months: [])) }
            let yi = out.count - 1
            if out[yi].months.last?.key != key { out[yi].months.append(MonthGroup(key: key, month: m, days: [])) }
            out[yi].months[out[yi].months.count - 1].days.append(g)
        }
        return out
    }

    var body: some View {
        let ys = years
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("연 · 월 · 일")
                    .font(AppFont.index(12))
                    .foregroundColor(Palette.onSurfaceVariant)
                    .padding(6)
                ForEach(Array(ys.enumerated()), id: \.element.year) { yi, y in
                    let yOpen = expanded[y.year] ?? (yi == 0)
                    IndexRow(label: "\(yOpen ? "▾" : "▸") \(y.year)", count: y.count, indent: 0, bold: true) {
                        expanded[y.year] = !yOpen
                    }
                    if yOpen {
                        ForEach(Array(y.months.enumerated()), id: \.element.key) { mi, m in
                            let mOpen = expanded[m.key] ?? (yi == 0 && mi == 0)
                            IndexRow(label: "\(mOpen ? "▾" : "▸") \(Int(m.month) ?? 0)월", count: m.count, indent: 10) {
                                expanded[m.key] = !mOpen
                                onJump(m.key)
                            }
                            if mOpen {
                                ForEach(m.days) { d in
                                    IndexRow(
                                        label: String(d.day.dropFirst(5)).replacingOccurrences(of: "-", with: "."),
                                        count: d.items.count,
                                        indent: 22,
                                        mono: true
                                    ) { onJump(d.day) }
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 8)
        }
    }
}

private struct IndexRow: View {
    let label: String
    let count: Int
    let indent: CGFloat
    var bold = false
    var mono = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 2) {
                Text(label)
                    .font(AppFont.index(bold ? 15 : mono ? 13 : 14, bold: bold))
                    .foregroundColor(mono ? Palette.onSurfaceVariant : Palette.onSurface)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text("\(count)")
                    .font(AppFont.index(12))
                    .foregroundColor(Palette.onSurfaceVariant)
            }
            .padding(.leading, 6 + indent)
            .padding(.trailing, 4)
            .padding(.vertical, 7)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/* 사진 칸: 누르면 열기, 꾹 누르면 공유·삭제 메뉴 */
private struct Tile: View {
    let entry: LogEntry
    let onOpen: () -> Void
    let onShare: () -> Void
    let onDelete: () -> Void

    var body: some View {
        Palette.surfaceVariant
            .aspectRatio(1, contentMode: .fit)
            .overlay { PhotoImage(file: entry.file, maxPixel: 400) }
            .clipShape(RoundedRectangle(cornerRadius: 4))
            .overlay(alignment: .topLeading) {
                if entry.hasPlace {
                    Image(systemName: "mappin")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.white)
                        .shadow(color: .black.opacity(0.5), radius: 1)
                        .padding(4)
                }
            }
            .overlay(alignment: .bottomTrailing) {
                if !entry.note.isBlank {
                    Circle().fill(Palette.stamp).frame(width: 7, height: 7).padding(5)
                }
            }
            .contentShape(RoundedRectangle(cornerRadius: 4))
            .onTapGesture(perform: onOpen)
            .contextMenu {
                Button(action: onShare) { Label("공유", systemImage: "square.and.arrow.up") }
                Button(role: .destructive, action: onDelete) { Label("삭제", systemImage: "trash") }
            }
            .accessibilityElement()
            .accessibilityLabel("\(entry.date) 사진")
            .accessibilityAddTraits(.isButton)
    }
}
