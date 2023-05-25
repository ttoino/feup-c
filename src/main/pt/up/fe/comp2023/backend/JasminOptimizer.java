package pt.up.fe.comp2023.backend;

import pt.up.fe.comp.jmm.jasmin.JasminResult;

public class JasminOptimizer {

    public JasminResult optimize(JasminResult jasminResult) {

        String jasminCode = jasminResult.getJasminCode();

        return new JasminResult(
                jasminResult.getClassName(),
                jasminCode.replaceAll("\\s*([ia])store[\\s_](\\d+)\\n\\s*\\1load[\\s_]\\2\\n|\\s*([ia])load[\\s_](\\d+)\\n\\s*\\3store[\\s_]\\4\\n", "\n"),
                jasminResult.getReports(),
                jasminResult.getConfig()
        );
    }
}
