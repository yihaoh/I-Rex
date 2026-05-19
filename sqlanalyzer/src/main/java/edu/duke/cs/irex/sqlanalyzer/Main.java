package edu.duke.cs.irex.sqlanalyzer;

import java.io.File;
import java.nio.file.Files;

import picocli.CommandLine;

public class Main {
    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    @CommandLine.Option(names = {"--host"}, defaultValue = "localhost", description = "database host name")
    private String dbHostname;

    @CommandLine.Option(names = {"--db"}, required = true, description = "database name")
    private String dbName;

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    private DBUsernameOption dbUsername;

    static class DBUsernameOption {
        @CommandLine.Option(names = "--user", description = "database user name")
        private String text;
        @CommandLine.Option(names = "--user:file", description = "database user name file")
        private File file;
    }

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    private DBPasswordOption dbPassword;

    static class DBPasswordOption {
        @CommandLine.Option(names = "--pass", description = "database password")
        private String text;
        @CommandLine.Option(names = "--pass:file", description = "database password file")
        private File file;
    }

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    private QueryOption query;

    @CommandLine.Option(names = {"--pageSize"}, defaultValue = "50", description = "page size")
    private int pageSize;

    static class QueryOption {
        @CommandLine.Option(names = "--query", description = "query string, without trailing ';'")
        private String text;
        @CommandLine.Option(names = "--query:file", description = "file containing the query string, without trailing ';'")
        private File file;
    }

    @CommandLine.Option(names = "--page_request:file", description = "file containing the page request")
    private File page_request_file;

    @CommandLine.Option(names = "--milestone_request:file", description = "file containing the milestone request")
    private File milestone_request_file;

    @CommandLine.Option(names = {"-a", "--analyze"}, arity = "0", description = "parse the query and generate context tree")
    private boolean performAnalyze;

    @CommandLine.Option(names = {"-f", "--page"}, arity = "0", description = "fetch a page from parameterized query with offset and limit")
    private boolean performFetch;

    @CommandLine.Option(names = {"-m", "--milestone"}, arity = "0", description = "fetch a milestone from parameterized query")
    private boolean performMilestone;

    public static void main(String[] args) throws Exception {
        Main app = new Main();
        new CommandLine(app).parseArgs(args);
        if (app.helpRequested) {
            CommandLine.usage(app, System.out);
            return;
        }
        String dbUsername = (app.dbUsername.text != null) ? app.dbUsername.text : (new String(Files.readString(app.dbUsername.file.toPath()))).trim();
        String dbPassword = (app.dbPassword.text != null) ? app.dbPassword.text : (new String(Files.readString(app.dbPassword.file.toPath()))).trim();
        int pageSize = app.pageSize;

        Analyzer analyzer = new Analyzer(app.dbHostname, app.dbName, dbUsername, dbPassword);
        if (app.performAnalyze) {
            String query = (app.query.text != null) ? app.query.text : (new String(Files.readString(app.query.file.toPath()))).trim();
            System.out.println(analyzer.analyzeToJson(query, pageSize));
        } else if (app.performFetch) {
            String page_request = new String(Files.readString(app.page_request_file.toPath())).trim();
            System.out.println(analyzer.executePageToJson(page_request));
        } else if (app.performMilestone) {
            String milestone_request = new String(Files.readString(app.milestone_request_file.toPath())).trim();
            System.out.println(analyzer.executeMilestoneToJson(milestone_request));
        }

        return;
    }
}
