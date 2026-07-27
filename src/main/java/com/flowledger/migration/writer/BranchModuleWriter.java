package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.migration.domain.ImportModule;
import com.flowledger.organization.dto.BranchDtos.BranchRequest;
import com.flowledger.organization.repository.BranchRepository;
import com.flowledger.organization.service.BranchService;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BranchModuleWriter implements ModuleWriter {
    private final BranchService branches;
    private final BranchRepository repo;

    public BranchModuleWriter(BranchService branches, BranchRepository repo) {
        this.branches = branches;
        this.repo = repo;
    }

    @Override
    public ImportModule module() {
        return ImportModule.BRANCH;
    }

    @Override
    public WriteResult write(UUID organizationId, Map<String, String> row) {
        String code = required(row, "branchCode").toUpperCase(Locale.ROOT);
        if (repo.existsByOrganizationIdAndCode(organizationId, code)) {
            return WriteResult.skipped("Branch already exists: " + code);
        }
        var created = branches.create(new BranchRequest(
                code,
                required(row, "branchName"),
                str(row, "addressLine1"),
                str(row, "city"),
                str(row, "state"),
                str(row, "postalCode"),
                str(row, "country"),
                str(row, "gstNumber"),
                str(row, "pan"),
                str(row, "phone"),
                str(row, "email"),
                true,
                bool(row, "headOffice"),
                bool(row, "headOffice")));
        return WriteResult.imported(created.id(), "BRANCH");
    }
}
