package pt.up.fe.comp2023.backend;

import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.ArrayList;
import java.util.regex.Pattern;

public class JasminOptimizer {

    private final Pattern gotoPattern = Pattern.compile("\\s*goto\\s+(\\w+)(\\n(\\w+:\\n)+\\1:)");
    private final Pattern loadStorePattern = Pattern.compile("\\s*([ia])load[\\s_](\\d+)\\n\\s*\\1store[\\s_]\\2\\n");

    public JasminResult optimize(JasminResult jasminResult) {

        String jasminCode = jasminResult.getJasminCode();

        jasminCode = this.performOptimization(jasminCode);

        var reports = new ArrayList<Report>();
        reports.add(new Report(ReportType.DEBUG, Stage.GENERATION, -1, -1, "Optimized Jasmin:\n" + jasminCode));

        return new JasminResult(jasminResult.getClassName(), jasminCode, reports, jasminResult.getConfig());
    }

    private String performOptimization(String jasminCode) {

        boolean codeChanged;
        do {
            int initialLength = jasminCode.length();

            jasminCode = this.stripRedundantStackOps(jasminCode);
            jasminCode = this.stripNoopGoto(jasminCode);

            int finalLength = jasminCode.length();

            codeChanged = finalLength != initialLength;
        } while (codeChanged);

        return jasminCode;
    }

    private String stripRedundantStackOps(String jasminCode) {
        var matcher = loadStorePattern.matcher(jasminCode);

        jasminCode = matcher.replaceAll("\n");

        return jasminCode;
    }

    private String stripNoopGoto(String jasminCode) {
        var matcher = gotoPattern.matcher(jasminCode);

        jasminCode = matcher.replaceAll("$2");

        return jasminCode;

    }
}
