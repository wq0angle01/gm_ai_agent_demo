import java.io.FileInputStream;
import java.sql.*;
import java.util.Properties;

/**
 * Temporary demonstration of strict vs non-strict handling of an unrepresentable character.
 * Creates one throwaway utf8mb3 table, changes sql_mode for THIS connection only, then drops it.
 * Run: java -cp <mysql-driver.jar> scripts/CharsetDemo.java secrets.properties
 */
public class CharsetDemo {

    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(args[0])) {
            p.load(in);
        }
        String db = p.getProperty("MYSQL_DB");
        String url = "jdbc:mysql://" + p.getProperty("MYSQL_HOST") + ":" + p.getProperty("MYSQL_PORT")
                + "/" + db + "?useSSL=false&characterEncoding=UTF-8&allowPublicKeyRetrieval=true";
        String text = "A" + new String(Character.toChars(0x1F3AE)) + "B";

        try (Connection c = DriverManager.getConnection(url, p.getProperty("MYSQL_USER"), p.getProperty("MYSQL_PASSWORD"))) {
            System.out.println("sql_mode            = " + one(c, "select @@sql_mode"));
            System.out.println("character_set_server= " + one(c, "select @@character_set_server"));

            exec(c, "drop table if exists _charset_demo_tmp");
            exec(c, "create table _charset_demo_tmp (name varchar(20)) character set utf8mb3");

            try (PreparedStatement s = c.prepareStatement("insert into _charset_demo_tmp values (?)")) {
                s.setString(1, text);
                s.executeUpdate();
                System.out.println("[strict]     insert unexpectedly succeeded");
            } catch (SQLException e) {
                System.out.println("[strict]     REJECTED -> " + e.getMessage());
            }

            exec(c, "delete from _charset_demo_tmp");
            exec(c, "set session sql_mode = ''");
            try (PreparedStatement s = c.prepareStatement("insert into _charset_demo_tmp values (?)")) {
                s.setString(1, text);
                System.out.println("[non-strict] insert returned rows=" + s.executeUpdate());
            }
            System.out.println("[non-strict] warnings = " + warnings(c));
            System.out.println("[non-strict] stored   = " + many(c,
                    "select concat('[',name,']  hex=',hex(name),'  char_length=',char_length(name)) from _charset_demo_tmp"));

            exec(c, "drop table _charset_demo_tmp");
            System.out.println("temp_table_dropped = " + "0".equals(one(c,
                    "select count(*) from information_schema.tables where table_schema='" + db + "' and table_name='_charset_demo_tmp'")));
        }
    }

    static void exec(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
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
                b.append(r.getString(1));
            }
        }
        return b.length() == 0 ? "EMPTY" : b.toString();
    }

    static String warnings(Connection c) throws SQLException {
        StringBuilder b = new StringBuilder();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("show warnings")) {
            while (r.next()) {
                b.append(r.getString(1)).append(" ").append(r.getInt(2)).append(" ").append(r.getString(3)).append(" | ");
            }
        }
        return b.length() == 0 ? "none" : b.toString();
    }
}
