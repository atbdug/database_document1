import cn.smallbun.screw.core.Configuration;
import cn.smallbun.screw.core.engine.EngineConfig;
import cn.smallbun.screw.core.engine.EngineFileType;
import cn.smallbun.screw.core.engine.EngineTemplateType;
import cn.smallbun.screw.core.execute.DocumentationExecute;
import cn.smallbun.screw.core.process.ProcessConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Demo {
    public static void main(String[] args) throws IOException, SQLException {

        String dbName = "db";
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("com.mysql.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://192.168.1.14:3306/" + dbName +"?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=GMT%2B8&useInformationSchema=true");
        config.setUsername("part");
        config.setPassword("123456");
        config.addDataSourceProperty("useInformationSchema", "true");
        config.setMinimumIdle(2);
        config.setMaximumPoolSize(5);
        DataSource ds = new HikariDataSource(config);
        //生成文件路径
        String userDir = System.getProperty("user.dir") + "\\src\\test\\java\\com\\pdool\\";
        System.out.println(userDir);
        SimpleDateFormat dataFormat = new SimpleDateFormat("yyyyMMdd");
        String versionStr = dataFormat.format(new Date());
        //忽略表
        List<String> ignoreTable = new ArrayList<String>();
        //忽略表前缀
        List<String> ignorePrefix = new ArrayList<String>();
        //忽略表后缀
        List<String> ignoreSuffix = new ArrayList<String>();
        ignoreSuffix.add("_test");
        ignoreSuffix.add("test");

        for (int i = 0; i < 10; i++) {
            ignoreSuffix.add(String.valueOf(i));
        }
        //生成HTML模板
        createHtml(ds, userDir, versionStr, ignoreTable, ignorePrefix, ignoreSuffix);
        //生成SQL建表语句
        createSql(dbName, ds, userDir, versionStr, ignoreTable, ignorePrefix, ignoreSuffix);
    }


    /**
     * 创建html
     * @param dataSource
     * @param userDir
     * @param versionStr
     * @param ignoreTable
     * @param ignorePrefix
     * @param ignoreSuffix
     */
    public static void createHtml(DataSource dataSource, String userDir, String versionStr, List<String> ignoreTable, List<String> ignorePrefix, List<String> ignoreSuffix) {
        //生成配置
        EngineConfig engineConfig = EngineConfig.builder()
                //生成文件路径
                .fileOutputDir(userDir)
                //打开目录
                .openOutputDir(false)
                //文件类型
                .fileType(EngineFileType.HTML)
                //生成模板实现
                .produceType(EngineTemplateType.freemarker)
                .build();

        ProcessConfig processConfig = ProcessConfig.builder()
                //忽略表名
                .ignoreTableName(ignoreTable)
                //忽略表前缀
                .ignoreTablePrefix(ignorePrefix)
                //忽略表后缀
                .ignoreTableSuffix(ignoreSuffix)
                .build();

        Configuration config = Configuration.builder()
                //版本
                .version(versionStr)
                //数据库描述
                .description("数据库文档")
                //数据源
                .dataSource(dataSource)
                //生成配置
                .engineConfig(engineConfig)
                //生成配置
                .produceConfig(processConfig).build();

        new DocumentationExecute(config).execute();
    }

    /**
     * 生成建表sql
     * @param dbName
     * @param dataSource
     * @param userDir
     * @param versionStr
     * @param ignoreTable
     * @param ignorePrefix
     * @param ignoreSuffix
     * @throws IOException
     * @throws SQLException
     */
    public static void createSql(String dbName, DataSource dataSource, String userDir, String versionStr, List<String> ignoreTable, List<String> ignorePrefix, List<String> ignoreSuffix) throws IOException, SQLException {
        Statement tmt = null;
        PreparedStatement pstmt = null;
        List<String> createSqlList = new ArrayList<String>();
        String sql = "select TABLE_NAME from INFORMATION_SCHEMA.TABLES where TABLE_SCHEMA = '"+dbName+"' and TABLE_TYPE = 'BASE TABLE'";
        tmt = dataSource.getConnection().createStatement();
        pstmt = dataSource.getConnection().prepareStatement(sql);
        ResultSet res = tmt.executeQuery(sql);
        while (res.next()) {
            String tableName = res.getString(1);
            if (tableName.contains("`")) {
                continue;
            }
            if (ignoreTable.contains(tableName)) {
                continue;
            }
            boolean isContinue = false;
            for (String prefix : ignorePrefix) {

                if (tableName.startsWith(prefix)) {
                    isContinue = true;
                    break;
                }
            }
            if (isContinue) {
                continue;
            }
            for (String suffix : ignoreSuffix) {
                if (tableName.startsWith(suffix)) {
                    isContinue = true;
                    break;
                }
            }
            if (isContinue) {
                continue;
            }
            ResultSet rs = pstmt.executeQuery("show create Table `" + tableName + "`");

            while (rs.next()) {
                createSqlList.add("DROP TABLE IF EXISTS '" + tableName + "'");
                createSqlList.add(rs.getString(2));
            }
        }

        String head = "-- 数据库建表语句 \r\n";
        head += "-- db:" + dbName + " version: " + versionStr + "\r\n";
        String collect = String.join(";\r\n", createSqlList);
        collect = head + collect + ";";
        string2file(collect, userDir + dbName + "_" + versionStr + ".sql");
    }

    public static void string2file(String collect, String dirStr) throws IOException {
        System.out.println("文件地址  " + dirStr);
        OutputStreamWriter osw = null;
        try {
            osw = new OutputStreamWriter(new FileOutputStream(new File(dirStr)), StandardCharsets.UTF_8);
            osw.write(collect);
            osw.flush();
        } finally {
            if (osw != null) {
                osw.close();
            }
        }
    }
}
