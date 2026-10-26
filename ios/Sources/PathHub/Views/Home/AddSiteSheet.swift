import SwiftUI

public struct AddSiteSheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var store: SiteStore

    @State private var urlInput: String = ""
    @State private var nameInput: String = ""
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil

    public init(store: SiteStore) {
        self.store = store
    }

    public var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Website URL (e.g. youtube.com)", text: $urlInput)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .keyboardType(.URL)

                    TextField("Display Name (optional)", text: $nameInput)
                } header: {
                    Text("Site Address")
                } footer: {
                    Text("We'll automatically fetch the site's title, icon, and normalize the address.")
                        .font(.caption)
                }

                if let error = errorMessage {
                    Section {
                        Text(error)
                            .foregroundColor(.red)
                            .font(.subheadline)
                    }
                }
            }
            .navigationTitle("Add Website")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }

                ToolbarItem(placement: .confirmationAction) {
                    if isLoading {
                        ProgressView()
                    } else {
                        Button("Add") {
                            addSite()
                        }
                        .disabled(urlInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        .bold()
                    }
                }
            }
        }
    }

    private func addSite() {
        errorMessage = nil
        isLoading = true

        Task { @MainActor in
            do {
                _ = try await store.addSite(urlInput: urlInput, nameInput: nameInput)
                isLoading = false
                dismiss()
            } catch {
                isLoading = false
                errorMessage = error.localizedDescription
            }
        }
    }
}
