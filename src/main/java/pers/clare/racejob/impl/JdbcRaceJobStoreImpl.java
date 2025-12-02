package pers.clare.racejob.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.InitializingBean;
import pers.clare.racejob.RaceJobStore;
import pers.clare.racejob.constant.RaceJobState;
import pers.clare.racejob.exception.RaceJobException;
import pers.clare.racejob.util.DataSourceSchemaUtil;
import pers.clare.racejob.vo.RaceJob;
import pers.clare.racejob.vo.RaceJobKey;
import pers.clare.racejob.vo.RaceJobStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Log4j2
public class JdbcRaceJobStoreImpl implements RaceJobStore, InitializingBean {

    private static final String FIND_ALL = "SELECT `group`,`name`, timezone,description,cron,after_group,after_name,enabled,`data` FROM race_job WHERE `instance` = ?";

    private static final String FIND_ALL_BY_GROUP = "SELECT `group`,`name`, timezone, description,cron,after_group,after_name,enabled,`data` FROM race_job WHERE `instance` = ? AND `group` = ?";

    private static final String FIND = "SELECT `group`,`name`, timezone,description,cron,after_group,after_name,enabled,`data` FROM race_job WHERE `instance` = ? AND `group` = ? AND `name` = ?";

    private static final String FIND_STATUS = "SELECT state, next_time, last_active_time, enabled FROM race_job WHERE `instance` = ? AND `group` = ? AND `name` = ?";

    private static final String INSERT = "INSERT INTO race_job(`instance`,`group`,`name`, timezone,description,cron,after_group,after_name,next_time,enabled,`data`) values(?,?,?,?,?,?,?,?,?,?,?)";

    private static final String UPDATE = "UPDATE race_job SET timezone=?,description=?,cron=?,after_group=?,after_name=?,next_time=?,enabled=?,`data`=? WHERE `instance` = ? AND `group` = ? AND `name` = ?";

    private static final String UPDATE_ACTIVE = "UPDATE race_job SET last_active_time=? WHERE `instance` = ? AND `group` = ? AND `name` = ?";

    private static final String UPDATE_RELEASE = "UPDATE race_job SET state=? WHERE `instance` = ? AND `group` = ? AND `name` = ? AND state = ? AND next_time<?";

    private static final String UPDATE_EXECUTING = "UPDATE race_job SET state=?,prev_time=start_time,next_time=?,start_time=?,end_time=0, last_active_time=? WHERE `instance` = ? AND `group` = ? AND `name` = ? AND enabled = true AND state = ? AND next_time<?";

    private static final String UPDATE_EXECUTING_BY_START_TIME = "UPDATE race_job SET prev_time=start_time,start_time=?,end_time=0 WHERE `instance` = ? AND `group` = ? AND `name` = ? AND start_time <> ?";

    private static final String UPDATE_STATE = "UPDATE race_job SET state=?, end_time=? WHERE `instance` = ? AND `group` = ? AND `name` = ?";

    private static final String UPDATE_ENABLED = "UPDATE race_job SET enabled = ? WHERE `instance` = ? AND `group` = ? AND `name` = ?";

    private static final String DELETE = "DELETE FROM race_job WHERE `instance` = ? AND `group` = ? AND `name` = ?";

    private static final TypeReference<Map<String, Object>> DATA_TYPE = new TypeReference<>() {
    };

    private static final ObjectMapper om = new ObjectMapper();

    private final DataSource dataSource;

    public JdbcRaceJobStoreImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        try {
            DataSourceSchemaUtil.init(dataSource);
        } catch (SQLException e) {
            log.error(e);
        }
    }

    @Override
    public List<RaceJob> findAll(String instance) {
        if (instance == null) return Collections.emptyList();
        List<RaceJob> result = new ArrayList<>();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(FIND_ALL);
            ps.setString(1, instance);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(to(rs));
            }
            return result;
        } catch (Exception e) {
            throw new RaceJobException(e);
        } finally {
            close(connection);
        }
    }

    @Override
    public List<RaceJob> findAll(String instance, String group) {
        if (instance == null || group == null) return Collections.emptyList();
        List<RaceJob> result = new ArrayList<>();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(FIND_ALL_BY_GROUP);
            ps.setString(1, instance);
            ps.setString(2, group);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(to(rs));
            }
            return result;
        } catch (Exception e) {
            throw new RaceJobException(e);
        } finally {
            close(connection);
        }
    }

    @Override
    public RaceJob find(String instance, RaceJobKey jobKey) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(FIND);
            setValue(ps, instance, jobKey.getGroup(), jobKey.getName());
            ResultSet rs = ps.executeQuery();
            return rs.next() ? to(rs) : null;
        } catch (Exception e) {
            throw new RaceJobException(e);
        } finally {
            close(connection);
        }
    }

    @Override
    public void insert(String instance, RaceJob entity, long nextTime) {
        try {
            String data = om.writeValueAsString(entity.getData());
            executeUpdate(INSERT, instance, entity.getGroup(), entity.getName(), entity.getTimezone(), entity.getDescription(), entity.getCron(), entity.getAfterGroup(), entity.getAfterName(), nextTime, entity.getEnabled(), data);
        } catch (RaceJobException e) {
            throw e;
        } catch (Exception e) {
            throw new RaceJobException(e);
        }
    }

    @Override
    public void update(String instance, RaceJob entity, long nextTime) {
        try {
            String data = om.writeValueAsString(entity.getData());
            executeUpdate(UPDATE, entity.getTimezone(), entity.getDescription(), entity.getCron()
                    , entity.getAfterGroup(), entity.getAfterName()
                    , nextTime, entity.getEnabled(), data
                    , instance, entity.getGroup(), entity.getName());
        } catch (RaceJobException e) {
            throw e;
        } catch (Exception e) {
            throw new RaceJobException(e);
        }
    }

    @Override
    public void updateActive(String instance, RaceJob entity, long activeTime) {
        try {
            executeUpdate(UPDATE_ACTIVE, activeTime, instance, entity.getGroup(), entity.getName());
        } catch (RaceJobException e) {
            throw e;
        } catch (Exception e) {
            throw new RaceJobException(e);
        }
    }


    @Override
    public void delete(String instance, RaceJobKey jobKey) {
        executeUpdate(DELETE, instance, jobKey.getGroup(), jobKey.getName());
    }

    @Override
    public void enable(String instance, RaceJobKey jobKey) {
        executeUpdate(UPDATE_ENABLED, 1, instance, jobKey.getGroup(), jobKey.getName());
    }

    @Override
    public void disable(String instance, RaceJobKey jobKey) {
        executeUpdate(UPDATE_ENABLED, 0, instance, jobKey.getGroup(), jobKey.getName());
    }

    @Override
    public int release(String instance, RaceJobKey jobKey, long nextTime) {
        return executeUpdate(UPDATE_RELEASE, RaceJobState.WAITING, instance, jobKey.getGroup(), jobKey.getName(), RaceJobState.EXECUTING, nextTime);
    }

    @Override
    public int compete(String instance, RaceJobKey jobKey, long nextTime, long startTime) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(UPDATE_EXECUTING);
            setValue(ps, RaceJobState.EXECUTING, nextTime, startTime, startTime, instance, jobKey.getGroup(), jobKey.getName(), RaceJobState.WAITING, nextTime);
            return ps.executeUpdate();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RaceJobException(e);
        } finally {
            close(connection);
        }
    }

    @Override
    public int compete(String instance, RaceJobKey jobKey, long startTime) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(UPDATE_EXECUTING_BY_START_TIME);
            setValue(ps, startTime, instance, jobKey.getGroup(), jobKey.getName(), startTime);
            return ps.executeUpdate();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RaceJobException(e);
        } finally {
            close(connection);
        }
    }

    @Override
    public int finish(String instance, RaceJobKey jobKey, long endTime) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(UPDATE_STATE);
            setValue(ps, RaceJobState.WAITING, endTime, instance, jobKey.getGroup(), jobKey.getName());
            return ps.executeUpdate();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RaceJobException(e);
        } finally {
            close(connection);
        }
    }

    public RaceJobStatus getStatus(String instance, RaceJobKey jobKey) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(FIND_STATUS);
            setValue(ps, instance, jobKey.getGroup(), jobKey.getName());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new RaceJobStatus(rs.getInt(1), rs.getLong(2), rs.getLong(3), rs.getBoolean(4));
            }
            return null;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RaceJobException(e);
        } finally {
            close(connection);
        }
    }

    private int executeUpdate(String sql, Object... parameters) {
        log.debug(sql);
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(sql);
            setValue(ps, parameters);
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new RaceJobException(e);
        } finally {
            close(connection);
        }
    }

    private void setValue(PreparedStatement ps, Object... parameters) throws SQLException {
        int index = 1;
        for (Object parameter : parameters) {
            ps.setObject(index++, parameter);
        }
    }

    private void close(Connection connection) {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
    }

    private RaceJob to(ResultSet rs) throws SQLException, JsonProcessingException {
        int index = 1;
        return new RaceJob(
                rs.getString(index++)
                , rs.getString(index++)
                , rs.getString(index++)
                , rs.getString(index++)
                , rs.getString(index++)
                , rs.getString(index++)
                , rs.getString(index++)
                , rs.getBoolean(index++)
                , om.readValue(rs.getString(index), DATA_TYPE)
        );
    }
}
