package io.github.noahhhx.mimos.api.account;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import java.util.UUID;
import org.openapitools.api.AccountApi;
import org.openapitools.model.AccountExport;
import org.openapitools.model.ImportReport;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/** Account export and import (ADR-0011) over the contract-generated {@link AccountApi}. */
@RestController
public class AccountController implements AccountApi {

    private final CurrentUserService currentUser;
    private final AccountExporter exporter;
    private final AccountImporter importer;
    private final ObjectMapper objectMapper;

    public AccountController(
            CurrentUserService currentUser,
            AccountExporter exporter,
            AccountImporter importer,
            ObjectMapper objectMapper) {
        this.currentUser = currentUser;
        this.exporter = exporter;
        this.importer = importer;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<AccountExport> exportAccount() {
        // Resolve (and on first sight create) the profile before the export's read-only snapshot.
        UUID profileId = currentUser.requireProfile().id();
        AccountExport document = exporter.export(profileId);
        String fileName = "mimos-export-" + document.getExportedAt().toLocalDate() + ".json";
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName)
                                .build()
                                .toString())
                .body(document);
    }

    @Override
    public ResponseEntity<ImportReport> importAccount(Object body) {
        UUID profileId = currentUser.requireProfile().id();
        return ResponseEntity.ok(importer.importInto(profileId, objectMapper.valueToTree(body)));
    }
}
