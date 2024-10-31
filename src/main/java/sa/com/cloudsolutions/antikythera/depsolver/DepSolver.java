package sa.com.cloudsolutions.antikythera.depsolver;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

public class DepSolver {

    private void solve() throws IOException {
        AbstractCompiler.preProcess();

    }

    public static void main(String[] args) throws IOException {
        Settings.loadConfigMap(new File("depsolver.yml"));
        DepSolver depSolver = new DepSolver();

    }
}
