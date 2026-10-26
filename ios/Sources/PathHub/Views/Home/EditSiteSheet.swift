import SwiftUI

public struct EditSiteSheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var store: SiteStore

    let site: Site
    @State private var name: String
    @State private var url: String
    @State private var errorMessage: String? = nil

    public init(store: SiteStore, site: Site) {
        self.store = store
        self.site = site
        _name = State(initialValue: site.name)
        _url = State(initialValue: site.url)
    }

    public var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("Details")) {
                    TextField("Display Name", text: $name)
                    TextField("Website URL", text: $url)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .keyboardType(.URL)
                }

                if let error = errorMessage {
                    Section {
                        Text(error)
                            .foregroundColor(.red)
                            .font(.subheadline)
                    }
                }
            }
            .navigationTitle("Edit Website")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }

                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        saveChanges()
                    }
                    .bold()
                }
            }
        }
    }

    private func saveChanges() {
        do {
            try store.updateSite(id: site.id, name: name, url: url)
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
