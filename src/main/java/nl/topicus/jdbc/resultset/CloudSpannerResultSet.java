package nl.topicus.jdbc.resultset;

import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.Date;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;

import nl.topicus.jdbc.util.CloudSpannerConversionUtil;

import com.google.cloud.spanner.Type;

public class CloudSpannerResultSet extends AbstractCloudSpannerResultSet
{
	private com.google.cloud.spanner.ResultSet resultSet;

	private boolean closed = false;

	private boolean wasNull = false;

	private boolean beforeFirst = true;

	private boolean nextCalledForMetaData = false;

	private boolean nextCalledForMetaDataResult = false;

	private Statement statement;

	public CloudSpannerResultSet(com.google.cloud.spanner.ResultSet resultSet)
	{
		this.resultSet = resultSet;
	}

	@Override
	public boolean wasNull() throws SQLException
	{
		return wasNull;
	}

	@Override
	public boolean next() throws SQLException
	{
		if (!beforeFirst && nextCalledForMetaData)
		{
			nextCalledForMetaData = false;
			return nextCalledForMetaDataResult;
		}
		beforeFirst = false;
		return resultSet.next();
	}

	@Override
	public void close() throws SQLException
	{
		resultSet.close();
		closed = true;
	}

	@Override
	public String getString(int columnIndex) throws SQLException
	{
		return isNull(columnIndex) ? null : resultSet.getString(columnIndex - 1);
	}

	@Override
	public boolean getBoolean(int columnIndex) throws SQLException
	{
		return isNull(columnIndex) ? false : resultSet.getBoolean(columnIndex - 1);
	}

	@Override
	public long getLong(int columnIndex) throws SQLException
	{
		return isNull(columnIndex) ? 0 : resultSet.getLong(columnIndex - 1);
	}

	@Override
	public double getDouble(int columnIndex) throws SQLException
	{
		return isNull(columnIndex) ? 0 : resultSet.getDouble(columnIndex - 1);
	}

	private BigDecimal toBigDecimal(double d, int scale)
	{
		return new BigDecimal(d, new MathContext(scale));
	}

	@Override
	public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException
	{
		return isNull(columnIndex) ? null : toBigDecimal(resultSet.getDouble(columnIndex - 1), scale);
	}

	@Override
	public byte[] getBytes(int columnIndex) throws SQLException
	{
		return isNull(columnIndex) ? null : resultSet.getBytes(columnIndex - 1).toByteArray();
	}

	@Override
	public Date getDate(int columnIndex) throws SQLException
	{
		return isNull(columnIndex) ? null : CloudSpannerConversionUtil.toSqlDate(resultSet.getDate(columnIndex - 1));
	}

	private Time toTime(com.google.cloud.spanner.Timestamp ts)
	{
		Time res = new Time(ts.getSeconds());
		return res;
	}

	@Override
	public Time getTime(int columnIndex) throws SQLException
	{
		return isNull(columnIndex) ? null : toTime(resultSet.getTimestamp(columnIndex - 1));
	}

	@Override
	public Timestamp getTimestamp(int columnIndex) throws SQLException
	{
		return isNull(columnIndex) ? null : CloudSpannerConversionUtil.toSqlTimestamp(resultSet
				.getTimestamp(columnIndex - 1));
	}

	@Override
	public String getString(String columnLabel) throws SQLException
	{
		return isNull(columnLabel) ? null : resultSet.getString(columnLabel);
	}

	@Override
	public boolean getBoolean(String columnLabel) throws SQLException
	{
		return isNull(columnLabel) ? false : resultSet.getBoolean(columnLabel);
	}

	@Override
	public long getLong(String columnLabel) throws SQLException
	{
		return isNull(columnLabel) ? 0 : resultSet.getLong(columnLabel);
	}

	@Override
	public double getDouble(String columnLabel) throws SQLException
	{
		return isNull(columnLabel) ? 0 : resultSet.getDouble(columnLabel);
	}

	@Override
	public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException
	{
		return isNull(columnLabel) ? null : toBigDecimal(resultSet.getDouble(columnLabel), scale);
	}

	@Override
	public byte[] getBytes(String columnLabel) throws SQLException
	{
		return isNull(columnLabel) ? null : resultSet.getBytes(columnLabel).toByteArray();
	}

	@Override
	public Date getDate(String columnLabel) throws SQLException
	{
		return isNull(columnLabel) ? null : CloudSpannerConversionUtil.toSqlDate(resultSet.getDate(columnLabel));
	}

	@Override
	public Time getTime(String columnLabel) throws SQLException
	{
		return isNull(columnLabel) ? null : toTime(resultSet.getTimestamp(columnLabel));
	}

	@Override
	public Timestamp getTimestamp(String columnLabel) throws SQLException
	{
		return isNull(columnLabel) ? null : CloudSpannerConversionUtil.toSqlTimestamp(resultSet
				.getTimestamp(columnLabel));
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException
	{
		if (beforeFirst)
		{
			nextCalledForMetaDataResult = resultSet.next();
			beforeFirst = false;
			nextCalledForMetaData = true;
		}
		return new CloudSpannerResultSetMetaData(resultSet);
	}

	@Override
	public int findColumn(String columnLabel) throws SQLException
	{
		return resultSet.getColumnIndex(columnLabel) + 1;
	}

	private boolean isNull(int columnIndex)
	{
		boolean res = resultSet.isNull(columnIndex - 1);
		wasNull = res;
		return res;
	}

	private boolean isNull(String columnName)
	{
		boolean res = resultSet.isNull(columnName);
		wasNull = res;
		return res;
	}

	@Override
	public BigDecimal getBigDecimal(int columnIndex) throws SQLException
	{
		return isNull(columnIndex) ? null : toBigDecimal(resultSet.getDouble(columnIndex - 1), 34);
	}

	@Override
	public BigDecimal getBigDecimal(String columnLabel) throws SQLException
	{
		return isNull(columnLabel) ? null : toBigDecimal(resultSet.getDouble(columnLabel), 34);
	}

	@Override
	public Statement getStatement() throws SQLException
	{
		return statement;
	}

	@Override
	public Date getDate(int columnIndex, Calendar cal) throws SQLException
	{
		return isNull(columnIndex) ? null : CloudSpannerConversionUtil.toSqlDate(resultSet.getDate(columnIndex - 1));
	}

	@Override
	public Date getDate(String columnLabel, Calendar cal) throws SQLException
	{
		return isNull(columnLabel) ? null : CloudSpannerConversionUtil.toSqlDate(resultSet.getDate(columnLabel));
	}

	@Override
	public Time getTime(int columnIndex, Calendar cal) throws SQLException
	{
		return isNull(columnIndex) ? null : toTime(resultSet.getTimestamp(columnIndex - 1));
	}

	@Override
	public Time getTime(String columnLabel, Calendar cal) throws SQLException
	{
		return isNull(columnLabel) ? null : toTime(resultSet.getTimestamp(columnLabel));
	}

	@Override
	public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException
	{
		return isNull(columnIndex) ? null : CloudSpannerConversionUtil.toSqlTimestamp(resultSet
				.getTimestamp(columnIndex - 1));
	}

	@Override
	public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException
	{
		return isNull(columnLabel) ? null : CloudSpannerConversionUtil.toSqlTimestamp(resultSet
				.getTimestamp(columnLabel));
	}

	@Override
	public boolean isClosed() throws SQLException
	{
		return closed;
	}

	@Override
	public byte getByte(int columnIndex) throws SQLException
	{
		return isNull(columnIndex) ? 0 : (byte) resultSet.getLong(columnIndex - 1);
	}

	@Override
	public short getShort(int columnIndex) throws SQLException
	{
		return isNull(columnIndex) ? 0 : (short) resultSet.getLong(columnIndex - 1);
	}

	@Override
	public int getInt(int columnIndex) throws SQLException
	{
		return isNull(columnIndex) ? 0 : (int) resultSet.getLong(columnIndex - 1);
	}

	@Override
	public float getFloat(int columnIndex) throws SQLException
	{
		return isNull(columnIndex) ? 0 : (float) resultSet.getDouble(columnIndex - 1);
	}

	@Override
	public byte getByte(String columnLabel) throws SQLException
	{
		return isNull(columnLabel) ? 0 : (byte) resultSet.getLong(columnLabel);
	}

	@Override
	public short getShort(String columnLabel) throws SQLException
	{
		return isNull(columnLabel) ? 0 : (short) resultSet.getLong(columnLabel);
	}

	@Override
	public int getInt(String columnLabel) throws SQLException
	{
		return isNull(columnLabel) ? 0 : (int) resultSet.getLong(columnLabel);
	}

	@Override
	public float getFloat(String columnLabel) throws SQLException
	{
		return isNull(columnLabel) ? 0 : (float) resultSet.getDouble(columnLabel);
	}

	@Override
	public Object getObject(String columnLabel) throws SQLException
	{
		Type type = resultSet.getColumnType(columnLabel);
		return isNull(columnLabel) ? null : getObject(type, columnLabel);
	}

	@Override
	public Object getObject(int columnIndex) throws SQLException
	{
		Type type = resultSet.getColumnType(columnIndex - 1);
		return isNull(columnIndex) ? null : getObject(type, columnIndex);
	}

	private Object getObject(Type type, String columnLabel) throws SQLException
	{
		return getObject(type, resultSet.getColumnIndex(columnLabel));
	}

	private Object getObject(Type type, int columnIndex) throws SQLException
	{
		if (type == Type.bool())
			return getBoolean(columnIndex);
		if (type == Type.bytes())
			return getBytes(columnIndex);
		if (type == Type.date())
			return getDate(columnIndex);
		if (type == Type.float64())
			return getFloat(columnIndex);
		if (type == Type.int64())
			return getInt(columnIndex);
		if (type == Type.string())
			return getString(columnIndex);
		if (type == Type.timestamp())
			return getTimestamp(columnIndex);
		throw new SQLException("Unknown type: " + type.toString());
	}

	@Override
	public SQLWarning getWarnings() throws SQLException
	{
		return null;
	}

	@Override
	public void clearWarnings() throws SQLException
	{
	}

}
