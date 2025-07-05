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
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link RsqlParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface RsqlParserVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link RsqlParser#input}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInput(RsqlParser.InputContext ctx);
	/**
	 * Visit a parse tree produced by {@link RsqlParser#or}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOr(RsqlParser.OrContext ctx);
	/**
	 * Visit a parse tree produced by {@link RsqlParser#and}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAnd(RsqlParser.AndContext ctx);
	/**
	 * Visit a parse tree produced by {@link RsqlParser#constraint}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstraint(RsqlParser.ConstraintContext ctx);
	/**
	 * Visit a parse tree produced by {@link RsqlParser#group}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGroup(RsqlParser.GroupContext ctx);
	/**
	 * Visit a parse tree produced by {@link RsqlParser#comparison}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitComparison(RsqlParser.ComparisonContext ctx);
	/**
	 * Visit a parse tree produced by {@link RsqlParser#selector}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelector(RsqlParser.SelectorContext ctx);
	/**
	 * Visit a parse tree produced by {@link RsqlParser#operator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOperator(RsqlParser.OperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link RsqlParser#arguments}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArguments(RsqlParser.ArgumentsContext ctx);
	/**
	 * Visit a parse tree produced by {@link RsqlParser#commaSepArguments}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCommaSepArguments(RsqlParser.CommaSepArgumentsContext ctx);
	/**
	 * Visit a parse tree produced by {@link RsqlParser#argument}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArgument(RsqlParser.ArgumentContext ctx);
}