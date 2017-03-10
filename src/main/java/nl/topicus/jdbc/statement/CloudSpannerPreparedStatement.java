package nl.topicus.jdbc.statement;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Timestamp;
import java.util.List;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.update.Update;
import nl.topicus.jdbc.CloudSpannerConnection;
import nl.topicus.jdbc.resultset.CloudSpannerResultSet;
import nl.topicus.jdbc.util.CloudSpannerConversionUtil;

import com.google.cloud.ByteArray;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Mutation.WriteBuilder;
import com.google.cloud.spanner.TransactionContext;
import com.google.cloud.spanner.TransactionRunner.TransactionCallable;
import com.google.cloud.spanner.ValueBinder;

/**
 * 
 * @author loite
 *
 */
public class CloudSpannerPreparedStatement extends AbstractCloudSpannerPreparedStatement
{
	private String sql;

	public CloudSpannerPreparedStatement(String sql, CloudSpannerConnection connection, DatabaseClient dbClient)
	{
		super(connection, dbClient);
		this.sql = sql;
	}

	@Override
	public ResultSet executeQuery() throws SQLException
	{
		Statement statement;
		try
		{
			statement = CCJSqlParserUtil.parse(sql);
		}
		catch (JSQLParserException e)
		{
			throw new SQLException("Error while parsing sql statement", e);
		}
		if (statement instanceof Select)
		{
			String namedSql = convertPositionalParametersToNamedParameters(sql);
			com.google.cloud.spanner.Statement.Builder builder = com.google.cloud.spanner.Statement
					.newBuilder(namedSql);
			setSelectParameters(((Select) statement).getSelectBody(), builder);
			com.google.cloud.spanner.ResultSet rs = getDbClient().singleUse().executeQuery(builder.build());

			return new CloudSpannerResultSet(rs);
		}
		throw new SQLException("SQL statement not suitable for executeQuery");
	}

	private String convertPositionalParametersToNamedParameters(String sql)
	{
		boolean inString = false;
		StringBuilder res = new StringBuilder(sql);
		int i = 0;
		int parIndex = 1;
		while (i < res.length())
		{
			char c = res.charAt(i);
			if (c == '\'')
			{
				inString = !inString;
			}
			else if (c == '?' && !inString)
			{
				res.replace(i, i + 1, "@p" + parIndex);
				parIndex++;
			}
			i++;
		}

		return res.toString();
	}

	private void setSelectParameters(SelectBody body, com.google.cloud.spanner.Statement.Builder builder)
	{
		body.accept(new SelectVisitor()
		{

			@Override
			public void visit(WithItem withItem)
			{
			}

			@Override
			public void visit(SetOperationList setOpList)
			{
			}

			@Override
			public void visit(PlainSelect plainSelect)
			{
				setWhereParameters(plainSelect.getWhere(), builder);
			}
		});
	}

	private void setWhereParameters(Expression where, com.google.cloud.spanner.Statement.Builder builder)
	{
		if (where != null)
		{
			where.accept(new ExpressionVisitorAdapter()
			{

				@Override
				public void visit(JdbcParameter parameter)
				{
					parameter.accept(new SpannerExpressionVisitorAdapter<com.google.cloud.spanner.Statement.Builder>(
							builder.bind("p" + parameter.getIndex())));
				}

				@Override
				public void visit(SubSelect subSelect)
				{
					setSelectParameters(subSelect.getSelectBody(), builder);
				}

			});
		}
	}

	@Override
	public int executeUpdate() throws SQLException
	{
		try
		{
			if (isDDLStatement())
			{
				String ddl = formatDDLStatement(sql);
				return executeDDL(ddl);
			}
			Statement statement = CCJSqlParserUtil.parse(sql);
			if (statement instanceof Insert)
			{
				return performInsert((Insert) statement);
			}
			else if (statement instanceof Update)
			{
				return performUpdate((Update) statement);
			}
			else if (statement instanceof Delete)
			{
				return performDelete((Delete) statement);
			}
			else
			{
				throw new SQLFeatureNotSupportedException();
			}
		}
		catch (JSQLParserException e)
		{
			throw new SQLException("Error while parsing sql statement " + sql, e);
		}
	}

	private static final String[] DDL_STATEMENTS = { "CREATE", "ALTER", "DROP" };

	/**
	 * Do a quick check if this SQL statement is a DDL statement
	 * 
	 * @return true if the SQL statement is a DDL statement
	 */
	private boolean isDDLStatement()
	{
		String ddl = this.sql.trim();
		ddl = ddl.substring(0, Math.min(8, ddl.length())).toUpperCase();
		for (String statement : DDL_STATEMENTS)
		{
			if (ddl.startsWith(statement))
				return true;
		}

		return false;
	}

	/**
	 * Does some formatting to DDL statements that might have been generated by
	 * standard SQL generators to make it compatible with Google Cloud Spanner.
	 * 
	 * @param sql
	 *            The sql to format
	 * @return The formatted DDL statement.
	 * @throws SQLException
	 */
	private String formatDDLStatement(String sql) throws SQLException
	{
		String res = sql.trim().toUpperCase();
		String[] parts = res.split("\\s+");
		if (parts.length >= 2)
		{
			String sqlWithSingleSpaces = String.join(" ", parts);
			if (sqlWithSingleSpaces.startsWith("CREATE TABLE"))
			{
				int primaryKeyIndex = res.indexOf(", PRIMARY KEY (");
				if (primaryKeyIndex > -1)
				{
					int endPrimaryKeyIndex = res.indexOf(")", primaryKeyIndex);
					String primaryKeySpec = res.substring(primaryKeyIndex + 2, endPrimaryKeyIndex + 1);
					res = res.replace(", " + primaryKeySpec, "");
					res = res + " " + primaryKeySpec;
				}
			}
		}

		return res;
	}

	private class SpannerExpressionVisitorAdapter<R> extends ExpressionVisitorAdapter
	{
		private ValueBinder<R> binder;

		private SpannerExpressionVisitorAdapter(ValueBinder<R> binder)
		{
			this.binder = binder;
		}

		private void setValue(Object value)
		{
			if (value == null)
			{
				// Set to null, type does not matter
				binder.to((Boolean) null);
			}
			else if (Boolean.class.isAssignableFrom(value.getClass()))
			{
				binder.to((Boolean) value);
			}
			else if (Byte.class.isAssignableFrom(value.getClass()))
			{
				binder.to(((Byte) value).longValue());
			}
			else if (Integer.class.isAssignableFrom(value.getClass()))
			{
				binder.to(((Integer) value).longValue());
			}
			else if (Long.class.isAssignableFrom(value.getClass()))
			{
				binder.to(((Long) value).longValue());
			}
			else if (Float.class.isAssignableFrom(value.getClass()))
			{
				binder.to(((Float) value).doubleValue());
			}
			else if (Double.class.isAssignableFrom(value.getClass()))
			{
				binder.to(((Double) value).doubleValue());
			}
			else if (BigDecimal.class.isAssignableFrom(value.getClass()))
			{
				binder.to(((BigDecimal) value).doubleValue());
			}
			else if (Date.class.isAssignableFrom(value.getClass()))
			{
				Date dateValue = (Date) value;
				binder.to(CloudSpannerConversionUtil.toCloudSpannerDate(dateValue));
			}
			else if (Timestamp.class.isAssignableFrom(value.getClass()))
			{
				Timestamp timeValue = (Timestamp) value;
				binder.to(CloudSpannerConversionUtil.toCloudSpannerTimestamp(timeValue));
			}
			else if (String.class.isAssignableFrom(value.getClass()))
			{
				binder.to((String) value);
			}
			else if (byte[].class.isAssignableFrom(value.getClass()))
			{
				binder.to(ByteArray.copyFrom((byte[]) value));
			}
			else
			{
				throw new IllegalArgumentException("Unsupported parameter type: " + value.getClass().getName() + " - "
						+ value.toString());
			}
		}

		@Override
		public void visit(JdbcParameter parameter)
		{
			Object value = getParameteStore().getParameter(parameter.getIndex());
			setValue(value);
		}

	}

	private int performInsert(Insert insert) throws SQLException
	{
		return getDbClient().readWriteTransaction().run(new TransactionCallable<Integer>()
		{

			@Override
			public Integer run(TransactionContext transaction) throws Exception
			{
				ItemsList items = insert.getItemsList();
				if (!(items instanceof ExpressionList))
				{
					throw new SQLException("Insert statement must contain a list of values");
				}
				List<Expression> expressions = ((ExpressionList) items).getExpressions();
				WriteBuilder builder = Mutation.newInsertBuilder(insert.getTable().getFullyQualifiedName());
				int index = 0;
				for (Column col : insert.getColumns())
				{
					expressions.get(index)
							.accept(new SpannerExpressionVisitorAdapter<WriteBuilder>(builder.set(col
									.getFullyQualifiedName())));
					index++;
				}
				transaction.buffer(builder.build());

				return 1;
			}
		});
	}

	private int performUpdate(Update update) throws SQLException
	{
		return getDbClient().readWriteTransaction().run(new TransactionCallable<Integer>()
		{

			@Override
			public Integer run(TransactionContext transaction) throws Exception
			{
				if (update.getTables().isEmpty())
					throw new SQLException("No table found in update statement");
				if (update.getTables().size() > 1)
					throw new SQLException("Update statements for multiple tables at once are not supported");
				String table = update.getTables().get(0).getFullyQualifiedName();
				List<Expression> expressions = update.getExpressions();
				WriteBuilder builder = Mutation.newUpdateBuilder(table);
				int index = 0;
				for (Column col : update.getColumns())
				{
					expressions.get(index)
							.accept(new SpannerExpressionVisitorAdapter<WriteBuilder>(builder.set(col
									.getFullyQualifiedName())));
					index++;
				}
				Expression where = update.getWhere();
				if (where != null)
				{
					where.accept(new ExpressionVisitorAdapter()
					{
						private Column col;

						@Override
						public void visit(Column column)
						{
							this.col = column;
						}

						@Override
						public void visit(JdbcParameter parameter)
						{
							parameter.accept(new SpannerExpressionVisitorAdapter<WriteBuilder>(builder.set(col
									.getFullyQualifiedName())));
						}

					});
				}

				transaction.buffer(builder.build());

				return 1;
			}
		});
	}

	private int performDelete(Delete delete) throws SQLException
	{
		return getDbClient().readWriteTransaction().run(new TransactionCallable<Integer>()
		{

			@Override
			public Integer run(TransactionContext transaction) throws Exception
			{
				String table = delete.getTable().getFullyQualifiedName();
				Expression where = delete.getWhere();
				Key.Builder keyBuilder = Key.newBuilder();
				if (where != null)
				{
					where.accept(new ExpressionVisitorAdapter()
					{
						@Override
						public void visit(JdbcParameter parameter)
						{
							Object value = getParameteStore().getParameter(parameter.getIndex());
							keyBuilder.appendObject(value);
						}

					});
				}
				transaction.buffer(Mutation.delete(table, keyBuilder.build()));

				return 1;
			}
		});
	}

	private int executeDDL(String ddl) throws SQLException
	{
		getConnection().executeDDL(ddl);
		return 0;
	}

	@Override
	public boolean execute() throws SQLException
	{
		throw new SQLFeatureNotSupportedException();
	}

}
