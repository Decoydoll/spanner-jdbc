package nl.topicus.sql;

import java.util.function.Supplier;
import java.util.regex.Pattern;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItemVisitorAdapter;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectVisitorAdapter;

public class SqlParser
{
	private Pattern commentPattern = Pattern.compile("//.*|/\\*((.|\\n)(?!=*/))+\\*/|--.*(?=\\n)", Pattern.DOTALL);

	private static final String[] DDL_STATEMENTS = { "CREATE", "ALTER", "DROP" };

	private static class BooleanSupplier implements Supplier<Boolean>
	{
		private boolean res = false;

		@Override
		public Boolean get()
		{
			return res;
		}

		public void set(Boolean res)
		{
			this.res = res;
		}
	};

	/**
	 * Determines whether the given sql statement must be executed in a single
	 * use read context. This must be done for queries against the information
	 * schema. This method should be used to set the
	 * <code>forceSingleUseReadContext</code> to true if necessary.
	 * 
	 * @param select
	 *            The sql statement to be examined.
	 * @return true iff the query should be run in singleUseReadContext
	 */
	public boolean determineForceSingleUseReadContext(Select select)
	{
		BooleanSupplier supplier = new BooleanSupplier();
		if (select.getSelectBody() != null)
		{
			select.getSelectBody().accept(new SelectVisitorAdapter()
			{
				@Override
				public void visit(PlainSelect plainSelect)
				{
					if (plainSelect.getFromItem() != null)
					{
						plainSelect.getFromItem().accept(new FromItemVisitorAdapter()
						{
							@Override
							public void visit(Table table)
							{
								if (table.getSchemaName() != null
										&& table.getSchemaName().equalsIgnoreCase("INFORMATION_SCHEMA"))
								{
									supplier.set(true);
								}
							}
						});
					}
				}

			});
		}
		return supplier.res;
	}

	/**
	 * Do a quick check if this SQL statement is a DDL statement
	 * 
	 * @param sqlTokens
	 *            The statement to check
	 * @return true if the SQL statement is a DDL statement
	 */
	public boolean isDDLStatement(String[] sqlTokens)
	{
		if (sqlTokens.length > 0)
		{
			for (String statement : DDL_STATEMENTS)
			{
				if (sqlTokens[0].equalsIgnoreCase(statement))
					return true;
			}
		}
		return false;
	}

	/**
	 * Remove comments from the given sql string and split it into parts based
	 * on all space characters
	 * 
	 * @param sql
	 *            The sql string to split into tokens
	 * @return String array with all the parts of the sql statement
	 */
	public String[] getTokens(String sql)
	{
		return getTokens(sql, 5);
	}

	/**
	 * Remove comments from the given sql string and split it into parts based
	 * on all space characters
	 * 
	 * @param sql
	 *            The sql statement to break into parts
	 * @param limit
	 *            The maximum number of times the pattern should be applied
	 * @return String array with all the parts of the sql statement
	 */
	public String[] getTokens(String sql, int limit)
	{
		String result = removeComments(sql);
		String generated = result.replaceFirst("=", " = ");
		return generated.split("\\s+", limit);
	}

	/**
	 * Remove all comments from the given sql string
	 * 
	 * @param sql
	 *            the sql string to strip for comments
	 * @return the sql statement without comments
	 */
	public String removeComments(String sql)
	{
		return commentPattern.matcher(sql).replaceAll("").trim();
	}

	/**
	 * 
	 * @param sqlTokens
	 *            the input to check
	 * @return true iff the given input is a select statement (the first token
	 *         is 'SELECT')
	 */
	public boolean isSelectStatement(String[] sqlTokens)
	{
		return sqlTokens.length > 0 && sqlTokens[0].equalsIgnoreCase("SELECT");
	}

	/**
	 * Does some formatting on the specified sql string to make it compatible
	 * with the sql parsing library. Specifically it removes any FORCE_INDEX
	 * directives and it adds a pseudo column update statement if the statement
	 * ends with an 'ON DUPLICATE KEY UPDATE' statement.
	 * 
	 * @param sql
	 *            the sql string to sanitize
	 * @return a sanitized sql string that can be parsed by the sql parsing
	 *         library
	 */
	public String sanitizeSQL(String sql)
	{
		// Add a pseudo update to the end if no columns have been specified in
		// an 'on duplicate key update'-statement
		if (sql.matches("(?is)\\s*INSERT\\s+.*\\s+ON\\s+DUPLICATE\\s+KEY\\s+UPDATE\\s*"))
		{
			sql = sql + " FOO=BAR";
		}
		// Remove @{FORCE_INDEX...} statements
		sql = sql.replaceAll("(?is)\\@\\{\\s*FORCE_INDEX.*\\}", "");

		return sql;
	}

	/**
	 * Does some formatting to DDL statements that might have been generated by
	 * standard SQL generators to make it compatible with Google Cloud Spanner.
	 * We also need to get rid of any comments, as Google Cloud Spanner does not
	 * accept comments in DDL-statements.
	 * 
	 * @param sql
	 *            The sql to format
	 * @return The formatted DDL statement.
	 */
	public String formatDDLStatement(String sql)
	{
		String result = removeComments(sql);
		String[] parts = getTokens(sql, 0);
		if (parts.length > 2 && parts[0].equalsIgnoreCase("create") && parts[1].equalsIgnoreCase("table"))
		{
			String sqlWithSingleSpaces = String.join(" ", parts);
			int primaryKeyIndex = sqlWithSingleSpaces.toUpperCase().indexOf(", PRIMARY KEY (");
			if (primaryKeyIndex > -1)
			{
				int endPrimaryKeyIndex = sqlWithSingleSpaces.indexOf(')', primaryKeyIndex);
				String primaryKeySpec = sqlWithSingleSpaces.substring(primaryKeyIndex + 2, endPrimaryKeyIndex + 1);
				sqlWithSingleSpaces = sqlWithSingleSpaces.replace(", " + primaryKeySpec, "");
				sqlWithSingleSpaces = sqlWithSingleSpaces + " " + primaryKeySpec;
				result = sqlWithSingleSpaces.replaceAll("\\s+\\)", ")");
			}
		}

		return result;
	}

}
