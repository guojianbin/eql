package org.n3r.eql.util;

import com.google.common.collect.Maps;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.val;
import org.n3r.eql.config.EqlConfig;
import org.n3r.eql.map.EqlRun;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;

@SuppressWarnings("unchecked")
public class EqlUtils {
    public static void compatibleWithUserToUsername(Map<String, String> params) {
        if (params.containsKey("username")) return;
        if (params.containsKey("user"))
            params.put("username", params.get("user"));
    }

    @SneakyThrows
    public static String getDriverNameFromConnection(DataSource dataSource) {
        @Cleanup val connection = dataSource.getConnection();
        return connection.getMetaData().getDriverName();
    }

    @SneakyThrows
    public static String getJdbcUrlFromConnection(DataSource dataSource) {
        @Cleanup val connection = dataSource.getConnection();
        return connection.getMetaData().getURL();
    }

    public static Map<String, Object> newExecContext(Object[] params, Object[] dynamics) {
        val execContext = Maps.<String, Object>newHashMap();
        execContext.put("_time", new Timestamp(System.currentTimeMillis()));
        execContext.put("_date", new java.util.Date());
        execContext.put("_host", HostAddress.getHost());
        execContext.put("_ip", HostAddress.getIp());
        execContext.put("_results", newArrayList());
        execContext.put("_lastResult", "");
        execContext.put("_params", params);
        if (params != null) {
            execContext.put("_paramsCount", params.length);
            for (int i = 0; i < params.length; ++i)
                execContext.put("_" + (i + 1), params[i]);
        }

        execContext.put("_dynamics", dynamics);
        if (dynamics != null)
            execContext.put("_dynamicsCount", dynamics.length);

        return execContext;
    }

    public static String trimLastUnusedPart(String sql) {
        val returnSql = S.trimRight(sql);
        val upper = S.upperCase(returnSql);
        if (S.endsWith(upper, "WHERE"))
            return returnSql.substring(0, sql.length() - "WHERE".length());

        if (S.endsWith(upper, "AND"))
            return returnSql.substring(0, sql.length() - "AND".length());

        if (S.endsWith(upper, "OR"))
            return returnSql.substring(0, sql.length() - "AND".length());

        return returnSql;
    }

    @SneakyThrows
    public static PreparedStatement prepareSQL(
            String sqlClassPath, EqlConfig eqlConfig, EqlRun eqlRun, String sqlId, String tagSqlId) {
        val log = Logs.createLogger(eqlConfig, sqlClassPath, sqlId, tagSqlId, "prepare");

        log.debug(eqlRun.getPrintSql());

        val conn = eqlRun.getConnection();
        val sql = eqlRun.getRunSql();
        val procedure = eqlRun.getSqlType().isProcedure();
        val ps = procedure ? conn.prepareCall(sql) : conn.prepareStatement(sql);

        setQueryTimeout(eqlConfig, ps);

        return ps;
    }

    public static int getConfigInt(EqlConfig eqlConfig, String key, int defaultValue) {
        val configValue = eqlConfig.getStr(key);
        if (S.isBlank(configValue)) return defaultValue;

        if (configValue.matches("\\d+")) return Integer.parseInt(configValue);
        return defaultValue;
    }

    @SneakyThrows
    public static void setQueryTimeout(EqlConfig eqlConfig, Statement stmt) {
        int queryTimeout = getConfigInt(eqlConfig, "query.timeout.seconds", 60);
        if (queryTimeout <= 0) queryTimeout = 60;

        stmt.setQueryTimeout(queryTimeout);
    }

    public static Iterable<?> evalCollection(String collectionExpr, EqlRun eqlRun) {
        val evaluator = eqlRun.getEqlConfig().getExpressionEvaluator();
        val value = evaluator.eval(collectionExpr, eqlRun);
        if (value == null) return null;

        if (value instanceof Iterable) return (Iterable<?>) value;
        if (value.getClass().isArray()) return newArrayList((Object[]) value);

        throw new RuntimeException(collectionExpr + " in "
                + eqlRun.getParamBean() + " is not an expression of a collection");
    }
}
