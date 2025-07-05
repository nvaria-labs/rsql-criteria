/*
 * Copyright 2024 Nvaria GmbH.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Generated from RsqlParser.g4 by ANTLR 4.13.2
package ch.nvaria.generated;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link RsqlParser}.
 */
public interface RsqlParserListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link RsqlParser#input}.
	 * @param ctx the parse tree
	 */
	void enterInput(RsqlParser.InputContext ctx);
	/**
	 * Exit a parse tree produced by {@link RsqlParser#input}.
	 * @param ctx the parse tree
	 */
	void exitInput(RsqlParser.InputContext ctx);
	/**
	 * Enter a parse tree produced by {@link RsqlParser#or}.
	 * @param ctx the parse tree
	 */
	void enterOr(RsqlParser.OrContext ctx);
	/**
	 * Exit a parse tree produced by {@link RsqlParser#or}.
	 * @param ctx the parse tree
	 */
	void exitOr(RsqlParser.OrContext ctx);
	/**
	 * Enter a parse tree produced by {@link RsqlParser#and}.
	 * @param ctx the parse tree
	 */
	void enterAnd(RsqlParser.AndContext ctx);
	/**
	 * Exit a parse tree produced by {@link RsqlParser#and}.
	 * @param ctx the parse tree
	 */
	void exitAnd(RsqlParser.AndContext ctx);
	/**
	 * Enter a parse tree produced by {@link RsqlParser#constraint}.
	 * @param ctx the parse tree
	 */
	void enterConstraint(RsqlParser.ConstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link RsqlParser#constraint}.
	 * @param ctx the parse tree
	 */
	void exitConstraint(RsqlParser.ConstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link RsqlParser#group}.
	 * @param ctx the parse tree
	 */
	void enterGroup(RsqlParser.GroupContext ctx);
	/**
	 * Exit a parse tree produced by {@link RsqlParser#group}.
	 * @param ctx the parse tree
	 */
	void exitGroup(RsqlParser.GroupContext ctx);
	/**
	 * Enter a parse tree produced by {@link RsqlParser#comparison}.
	 * @param ctx the parse tree
	 */
	void enterComparison(RsqlParser.ComparisonContext ctx);
	/**
	 * Exit a parse tree produced by {@link RsqlParser#comparison}.
	 * @param ctx the parse tree
	 */
	void exitComparison(RsqlParser.ComparisonContext ctx);
	/**
	 * Enter a parse tree produced by {@link RsqlParser#selector}.
	 * @param ctx the parse tree
	 */
	void enterSelector(RsqlParser.SelectorContext ctx);
	/**
	 * Exit a parse tree produced by {@link RsqlParser#selector}.
	 * @param ctx the parse tree
	 */
	void exitSelector(RsqlParser.SelectorContext ctx);
	/**
	 * Enter a parse tree produced by {@link RsqlParser#operator}.
	 * @param ctx the parse tree
	 */
	void enterOperator(RsqlParser.OperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link RsqlParser#operator}.
	 * @param ctx the parse tree
	 */
	void exitOperator(RsqlParser.OperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link RsqlParser#arguments}.
	 * @param ctx the parse tree
	 */
	void enterArguments(RsqlParser.ArgumentsContext ctx);
	/**
	 * Exit a parse tree produced by {@link RsqlParser#arguments}.
	 * @param ctx the parse tree
	 */
	void exitArguments(RsqlParser.ArgumentsContext ctx);
	/**
	 * Enter a parse tree produced by {@link RsqlParser#commaSepArguments}.
	 * @param ctx the parse tree
	 */
	void enterCommaSepArguments(RsqlParser.CommaSepArgumentsContext ctx);
	/**
	 * Exit a parse tree produced by {@link RsqlParser#commaSepArguments}.
	 * @param ctx the parse tree
	 */
	void exitCommaSepArguments(RsqlParser.CommaSepArgumentsContext ctx);
	/**
	 * Enter a parse tree produced by {@link RsqlParser#argument}.
	 * @param ctx the parse tree
	 */
	void enterArgument(RsqlParser.ArgumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link RsqlParser#argument}.
	 * @param ctx the parse tree
	 */
	void exitArgument(RsqlParser.ArgumentContext ctx);
}