/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.dao.sql.transformer;

import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.internal.dao.sql.transformer.SQLFunctionTransformer;
import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.StringUtil;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Manuel de la Peña
 */
public class OracleSQLTransformerLogic extends BaseSQLTransformerLogic {

	public OracleSQLTransformerLogic(DB db) {
		super(db);

		Function[] functions = {
			getAggregationFunction(), getBooleanFunction(),
			getCastClobTextFunction(), getCastLongFunction(),
			getCastTextFunction(), getConcatFunction(),
			getDropTableIfExistsTextFunction(), getIntegerDivisionFunction(),
			getNullDateFunction(), _getEscapeFunction(),
			_getNotEqualsBlankStringFunction(), _getOrderByClobFunction(),
			_getPushNotificationsDeviceTokenIndexFunction()
		};

		if (!db.isSupportsStringCaseSensitiveQuery()) {
			functions = ArrayUtil.append(functions, getLowerFunction());
		}

		setFunctions(functions);
	}

	@Override
	protected Function<String, String> getConcatFunction() {
		SQLFunctionTransformer sqlFunctionTransformer =
			new SQLFunctionTransformer(
				"CONCAT(", StringPool.BLANK, " || ", StringPool.BLANK);

		return sqlFunctionTransformer::transform;
	}

	@Override
	protected String replaceCastClobText(Matcher matcher) {
		return matcher.replaceAll("DBMS_LOB.SUBSTR($1, 4000, 1)");
	}

	@Override
	protected String replaceCastText(Matcher matcher) {
		return matcher.replaceAll("CAST($1 AS VARCHAR(4000))");
	}

	@Override
	protected String replaceDropTableIfExistsText(Matcher matcher) {
		String dropTableIfExists = StringBundler.concat(
			"BEGIN\n", "EXECUTE IMMEDIATE 'DROP TABLE $1';\n", "EXCEPTION\n",
			"WHEN OTHERS THEN\n", "IF SQLCODE != -942 THEN\n", "RAISE;\n",
			"END IF;\n", "END;\n", "/");

		return matcher.replaceAll(dropTableIfExists);
	}

	@Override
	protected String replaceIntegerDivision(Matcher matcher) {
		return matcher.replaceAll("TRUNC($1 / $2)");
	}

	private Function<String, String> _getEscapeFunction() {
		return (String sql) -> StringUtil.replace(
			sql, "LIKE ?", "LIKE ? ESCAPE '\\'");
	}

	private Function<String, String> _getNotEqualsBlankStringFunction() {
		return (String sql) -> StringUtil.replace(
			sql, " != ''", " IS NOT NULL");
	}

	private Function<String, String> _getOrderByClobFunction() {
		return (sql) -> {
			int orderByPos = sql.indexOf("ORDER BY");

			if (orderByPos == -1) {
				return sql;
			}

			String orderByClause = sql.substring(orderByPos);

			Pattern pattern = Pattern.compile(
				"(\\w+\\.(?:name|description|title|value))");

			Matcher matcher = pattern.matcher(orderByClause);

			StringBuffer sb = new StringBuffer();

			while (matcher.find()) {
				matcher.appendReplacement(
					sb, "DBMS_LOB.SUBSTR(" + matcher.group(0) + ", 2000)");
			}

			matcher.appendTail(sb);

			return sql.substring(0, orderByPos) + sb.toString();
		};
	}

	private Function<String, String> _getPushNotificationsDeviceTokenIndexFunction() {
		return (sql) -> {
			if (!sql.contains("PushNotificationsDevice")) {
				return sql;
			}

			Pattern pattern = Pattern.compile(
				"CREATE UNIQUE INDEX (\\w+) ON PushNotificationsDevice\\s*\\(token\\[\\$COLUMN_LENGTH:\\d+\\$\\]\\)",
				Pattern.CASE_INSENSITIVE);

			Matcher matcher = pattern.matcher(sql);

			return matcher.replaceAll(
				"CREATE UNIQUE INDEX $1 ON PushNotificationsDevice (DBMS_LOB.SUBSTR(token, 512, 1))");
		};
	}

}