import java.io.FileInputStream;
import java.sql.*;
import java.util.Properties;

/**
 * Read-only charset probe, run with: java -cp <mysql-driver.jar> scripts/DbProbe.java secrets.properties
 * Connects without selecting a database so a missing schema stays diagnosable.
 */
public class DbProbe {

    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(args[0])) {
            p.load(in);
        }
        String db = p.getProperty("MYSQL_DB");
        String url = "jdbc:mysql://" + p.getProperty("MYSQL_HOST") + ":" + p.getProperty("MYSQL_PORT")
                + "/?useSSL=false&characterEncoding=UTF-8&allowPublicKeyRetrieval=true";

        try (Connection c = DriverManager.getConnection(url, p.getProperty("MYSQL_USER"), p.getProperty("MYSQL_PASSWORD"))) {
            say("server_version", one(c, "select version()"));
            say("character_set_server", one(c, "select @@character_set_server"));
            say("character_set_database_of_connection", one(c, "select @@character_set_database"));
            say("collation_connection", one(c, "select @@collation_connection"));
            say("target_db_exists", one(c, "select count(*) from information_schema.schemata where schema_name='" + db + "'"));
            say("visible_databases", many(c, "select schema_name from information_schema.schemata"));
            say("tables_in_target_db", many(c, "select table_name from information_schema.tables where table_schema='" + db + "'"));
            say("db_default_collation", one(c, "select default_collation_name from information_schema.schemata where schema_name='" + db + "'"));
            say("player_table_collation", one(c, "select table_collation from information_schema.tables where table_schema='" + db + "' and table_name='player'"));
            say("player_columns_charset", many(c, "select concat(column_name,':',ifnull(character_set_name,'-'),':',ifnull(character_maximum_length,'-'))"
                    + " from information_schema.columns where table_schema='" + db + "' and table_name='player' order by ordinal_position"));
            say("stored_nickname_hex", many(c, "select concat(id,'=',hex(nickname)) from `" + db + "`.player order by id"));
        }
    }

    static void say(String k, String v) {
        System.out.println(k + " = " + v);
    }

    static String one(Connection c, String q) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) {
            return r.next() ? String.valueOf(r.getObject(1)) : "no-row";
        }
    }

    static String many(Connection c, String q) throws SQLException {
        StringBuilder b = new StringBuilder();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) {
            while (r.next()) {
                if (b.length() > 0) {
                    b.append(" | ");
                }
                b.append(r.getString(1));
            }
        }
        return b.length() == 0 ? "EMPTY" : b.toString();
    }
}
