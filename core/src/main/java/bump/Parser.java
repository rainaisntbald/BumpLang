package bump;

import ast.*;
import ast.SwitchCase;
import ast.SwitchStmt;
import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final ParserCursor cursor;
    private final ParserLookahead lookahead;
    private int functionDepth = 0;
    private int syntheticVariableCounter = 0;

    public Parser(List<Token> tokens) {
        this.cursor = new ParserCursor(tokens);
        this.lookahead = new ParserLookahead(this);
    }

    public Stmt parseStatement() {
        if (match(TokenType.IF)) return parseIfStatement(lastToken());
        if (match(TokenType.WHILE)) return parseWhileStatement(lastToken());
        if (match(TokenType.FOR)) return parseForStatement(lastToken());
        if (match(TokenType.TRY)) return parseTryStatement(lastToken());
        if (match(TokenType.JAVABLOCK)) return new JavaBlockStmt(lastToken().getText()).at(lastToken());
        if (match(TokenType.LBRACE)) return new BlockStmt(parseBlock()).at(lastToken());
        if (match(TokenType.RETURN)) {
            Token keyword = lastToken();
            if (functionDepth == 0) throw error(keyword, "Cannot return from top-level code.");
            Stmt stmt = parseReturnStatement(keyword);
            expect(TokenType.SEMICOLON);
            return stmt;
        }
        if (match(TokenType.BREAK)) {
            BreakStmt stmt = new BreakStmt().at(lastToken());
            expect(TokenType.SEMICOLON);
            return stmt;
        }
        if (match(TokenType.CONTINUE)) {
            ContinueStmt stmt = new ContinueStmt().at(lastToken());
            expect(TokenType.SEMICOLON);
            return stmt;
        }
        if (match(TokenType.FUN)) return parseFunctionDeclaration();
        if (match(TokenType.SWITCH)) return parseSwitchStatement(lastToken());
        if (match(TokenType.CLASS)) return parseClassDeclaration(lastToken());
        if (match(TokenType.ENUM)) return parseEnumDeclaration(lastToken());
        if (match(TokenType.ABSTRACT)) {
            Token keyword = lastToken();
            if (!match(TokenType.CLASS)) {
                throw error(peek(), "Expected 'class' after 'abstract'.");
            }
            return parseClassDeclaration(keyword, true);
        }
        if (match(TokenType.EXTEND)) return parseExtendDeclaration(lastToken());
        if (looksLikeTypedDeclaration()) return parseTypedVariableDeclaration();

        Expr expr = parseExpression();
        expect(TokenType.SEMICOLON);
        return new ExpressionStmt(expr).at(expr);
    }

    public List<Stmt> parseProgram() {
        List<Stmt> statements = new ArrayList<>();
        while (!cursor.isAtEnd()) {
            statements.add(parseStatement());
        }

        return statements;
    }

    private Stmt parseIfStatement(Token keyword) {
        Expr condition = parseWithParens();
        Stmt thenBranch = parseStatement();
        Stmt elseBranch = match(TokenType.ELSE) ? parseStatement() : null;
        return new IfStmt(condition, thenBranch, elseBranch).at(keyword);
    }

    private Stmt parseWhileStatement(Token keyword) {
        Expr condition = parseWithParens();
        Stmt block = parseStatement();
        return new WhileStmt(condition, block).at(keyword);
    }

    private Stmt parseForStatement(Token keyword) {
        expect(TokenType.LPAREN);
        if (looksLikeEnhancedForHeader()) {
            return parseEnhancedForStatement(keyword);
        }
        Stmt initializer = null;
        if (!match(TokenType.SEMICOLON)) {
            if (looksLikeTypedDeclaration()) {
                initializer = parseTypedVariableDeclaration();
            } else {
                Expr initialExpr = parseExpression();
                expect(TokenType.SEMICOLON);
                initializer = new ExpressionStmt(initialExpr).at(initialExpr);
            }
        }

        Expr condition = null;
        if (!match(TokenType.SEMICOLON)) {
            condition = parseExpression();
            expect(TokenType.SEMICOLON);
        }

        Stmt increment = null;
        if (!match(TokenType.RPAREN)) {
            Expr incrementExpr = parseExpression();
            expect(TokenType.RPAREN);
            increment = new ExpressionStmt(incrementExpr).at(incrementExpr);
        }
        Stmt body = parseStatement();

        return new ForStmt(initializer, condition, increment, body).at(keyword);
    }

    private Stmt parseEnhancedForStatement(Token keyword) {
        String itemTypeName = parseTypeName();
        Token itemName = expect(TokenType.ID);
        expect(TokenType.COLON);
        Expr source = parseExpression();
        expect(TokenType.RPAREN);
        Stmt body = parseStatement();

        String iteratorName = "__iter_" + syntheticVariableCounter++;

        Expr iterCall = new CallExpr(
                new GetExpr(source, "iter").at(keyword),
                List.of(),
                List.of()
        ).at(keyword);
        VariableDeclarationStmt iteratorDecl = new VariableDeclarationStmt(null, iteratorName, iterCall).at(keyword);

        Expr hasNext = new CallExpr(
                new GetExpr(new VariableExpr(iteratorName).at(keyword), "has_next").at(keyword),
                List.of(),
                List.of()
        ).at(keyword);
        Expr nextValue = new CallExpr(
                new GetExpr(new VariableExpr(iteratorName).at(keyword), "next").at(keyword),
                List.of(),
                List.of()
        ).at(keyword);
        VariableDeclarationStmt itemDecl = new VariableDeclarationStmt(null, itemName.getText(), nextValue).at(itemName);

        List<Stmt> whileBodyStatements = new ArrayList<>();
        whileBodyStatements.add(itemDecl);
        whileBodyStatements.add(body);
        WhileStmt loop = new WhileStmt(hasNext, new BlockStmt(whileBodyStatements).at(keyword)).at(keyword);

        return new BlockStmt(List.of(iteratorDecl, loop)).at(keyword);
    }

    private Stmt parseReturnStatement(Token keyword) {
        Expr value = null;
        if (!check(TokenType.SEMICOLON)) {
            value = parseExpression();
        }
        return new ReturnStmt(value).at(keyword);
    }

    private Stmt parseTryStatement(Token keyword) {
        BlockStmt tryBlock = parseRequiredBlock("Expected '{' after 'try'.");
        Parameter catchParameter = null;
        BlockStmt catchBlock = null;
        BlockStmt finallyBlock = null;

        if (match(TokenType.CATCH)) {
            if (match(TokenType.LPAREN)) {
                Token typeToken = expect(TokenType.ID);
                Token nameToken = expect(TokenType.ID);
                expect(TokenType.RPAREN);
                catchParameter = parameter(typeToken.getText(), typeToken, nameToken);
            }
            catchBlock = parseRequiredBlock("Expected '{' after 'catch'.");
        }
        if (match(TokenType.FINALLY)) {
            finallyBlock = parseRequiredBlock("Expected '{' after 'finally'.");
        }

        if (catchBlock == null && finallyBlock == null) {
            throw error(peek(), "Expected 'catch' or 'finally' after try block.");
        }

        return new TryStmt(tryBlock, catchParameter, catchBlock, finallyBlock).at(keyword);
    }

    private Stmt parseSwitchStatement(Token start) {
        expect(TokenType.LPAREN);
        Expr expression = parseExpression();
        expect(TokenType.RPAREN);
        expect(TokenType.LBRACE);
        List<SwitchCase> cases = new ArrayList<>();
        while (!match(TokenType.RBRACE)) {
            if (match(TokenType.CASE)) {
                List<Expr> labels = new ArrayList<>();
                do {
                    labels.add(parseExpression());
                } while (match(TokenType.COMMA));
                expect(TokenType.COLON);
                List<Stmt> body = new ArrayList<>();
                while (!check(TokenType.CASE) && !check(TokenType.DEFAULT) && !check(TokenType.RBRACE)) {
                    body.add(parseStatement());
                }
                cases.add(new SwitchCase(labels, body, false));
            } else if (match(TokenType.DEFAULT)) {
                expect(TokenType.COLON);
                List<Stmt> body = new ArrayList<>();
                while (!check(TokenType.CASE) && !check(TokenType.DEFAULT) && !check(TokenType.RBRACE)) {
                    body.add(parseStatement());
                }
                cases.add(new SwitchCase(List.of(), body, true));
            } else {
                throw error(peek(), "Expected 'case' or 'default' in switch.");
            }
        }
        return new SwitchStmt(expression, cases).at(start);
    }

    private Stmt parseFunctionDeclaration() {
        return parseFunctionDeclaration(false);
    }

    private Stmt parseFunctionDeclaration(boolean isPrivate) {
        String returnTypeName = parseTypeName();
        Token name = expect(TokenType.ID);
        List<TypeParameter> typeParameters = parseTypeParameters();
        FunctionLiteralExpr function = parseFunctionBody(name, returnTypeName, true, typeParameters);
        return new VariableDeclarationStmt("Function", name.getText(), function, isPrivate).at(name);
    }

    private Stmt parseClassDeclaration(Token keyword) {
        return parseClassDeclaration(keyword, false);
    }

    private Stmt parseClassDeclaration(Token keyword, boolean isAbstract) {
        Token name = expect(TokenType.ID);
        List<TypeParameter> typeParameters = parseTypeParameters();
        String superclassName = null;
        if (match(TokenType.EXTENDS)) {
            superclassName = parseTypeName();
        }
        List<String> interfaceNames = new ArrayList<>();
        if (match(TokenType.IMPLEMENTS)) {
            do {
                interfaceNames.add(parseTypeName());
            } while (match(TokenType.COMMA));
        }
        expect(TokenType.LBRACE);
        List<VariableDeclarationStmt> fields = new ArrayList<>();
        List<VariableDeclarationStmt> methods = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !cursor.isAtEnd()) {
            parseClassMember(name, fields, methods, isAbstract);
        }
        expect(TokenType.RBRACE);
        return new ClassStmt(name.getText(), isAbstract, typeParameters, superclassName, interfaceNames, fields, methods).at(name);
    }

    private Stmt parseEnumDeclaration(Token keyword) {
        Token name = expect(TokenType.ID);
        List<TypeParameter> typeParameters = parseTypeParameters();
        expect(TokenType.LBRACE);
        List<EnumVariant> variants = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !cursor.isAtEnd()) {
            Token variant = expect(TokenType.ID);
            String payloadTypeName = null;
            String payloadName = null;
            Expr value = null;
            if (match(TokenType.LPAREN)) {
                if (check(TokenType.ID) && peek(1) != null && peek(1).getType() == TokenType.RPAREN) {
                    Token payload = expect(TokenType.ID);
                    payloadTypeName = "Object";
                    payloadName = payload.getText();
                } else {
                    payloadTypeName = parseTypeName();
                    Token payload = expect(TokenType.ID);
                    payloadName = payload.getText();
                }
                expect(TokenType.RPAREN);
            }
            if (match(TokenType.ASSIGN)) {
                if (payloadName != null) {
                    throw error(lastToken(), "Enum variant cannot use both payload and attached value.");
                }
                value = parseExpression();
            }
            variants.add(new EnumVariant(variant.getText(), payloadTypeName, payloadName, value));
            if (!match(TokenType.COMMA)) {
                break;
            }
            if (check(TokenType.RBRACE)) {
                break;
            }
        }
        expect(TokenType.RBRACE);
        return new EnumStmt(name.getText(), typeParameters, variants).at(name);
    }

    private Stmt parseExtendDeclaration(Token keyword) {
        Token className = expect(TokenType.ID);
        expect(TokenType.LBRACE);
        List<VariableDeclarationStmt> methods = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !cursor.isAtEnd()) {
            if (!match(TokenType.FUN)) {
                throw error(peek(), "Expected method definition in extension.");
            }
            methods.add((VariableDeclarationStmt) parseFunctionDeclaration(false));
        }
        expect(TokenType.RBRACE);
        return new ExtendStmt(className.getText(), methods).at(className);
    }

    private FunctionLiteralExpr parseFunctionBody(Token start, String returnTypeName, boolean declarationContext, List<TypeParameter> typeParameters) {
        functionDepth++;
        try {
            expect(TokenType.LPAREN);
            List<Parameter> parameters = parseParameters();
            expect(TokenType.RPAREN);
            List<Stmt> body;
            if (match(TokenType.FATARROW)) {
                Expr value = parseExpression();
                body = List.of(new ReturnStmt(value).at(value));
                if (declarationContext) {
                    expect(TokenType.SEMICOLON);
                }
            } else {
                expect(TokenType.LBRACE);
                body = parseBlock();
            }
            return new FunctionLiteralExpr(returnTypeName, typeParameters, parameters, body, false).at(start);
        } finally {
            functionDepth--;
        }
    }

    private List<Stmt> parseBlock() {
        List<Stmt> statements = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !cursor.isAtEnd()) {
            statements.add(parseStatement());
        }
        expect(TokenType.RBRACE);
        return statements;
    }

    private Expr parseExpression() {
        return parseAssignment();
    }

    private Expr parseAssignment() {
        Expr expr = parseOr();

        if (matchAny(TokenType.ASSIGN, TokenType.PLUSASSIGN, TokenType.MINUSASSIGN, TokenType.MULASSIGN, TokenType.DIVASSIGN)) {
            Token operator = lastToken();
            Expr value = parseAssignment();
            if (operator.getType() != TokenType.ASSIGN) {
                ensureAssignableTarget(expr, operator, "Invalid assignment target.");
                return new CompoundSetExpr(expr, operator.getType(), value).at(expr);
            }

            if (expr instanceof VariableExpr v) {
                return new SetExpr(null, v.name, value).at(v);
            } else if (expr instanceof GetExpr g) {
                return new SetExpr(g.object, g.name, value).at(g);
            } else if (expr instanceof ArrayIndex ai) {
                return new SetIndex(ai.array, ai.index, value).at(ai);
            }

            throw error(lastToken(), "Invalid assignment target.");
        }

        return expr;
    }

    private VariableDeclarationStmt parseTypedVariableDeclaration() {
        return parseTypedVariableDeclaration(false);
    }

    private VariableDeclarationStmt parseTypedVariableDeclaration(boolean isPrivate) {
        String typeName = parseTypeName();
        Token name = expect(TokenType.ID);
        Expr initializer = null;
        if (match(TokenType.ASSIGN)) {
            initializer = parseExpression();
        }
        expect(TokenType.SEMICOLON);
        return new VariableDeclarationStmt(typeName, name.getText(), initializer, isPrivate).at(name);
    }

    private Expr parseOr() {
        Expr left = parseAnd();
        while(match(TokenType.OR)) {
            left = new Or(left, parseAnd()).at(left);
        }
        return left;
    }

    private Expr parseAnd() {
        Expr left = parseComparison();
        while(match(TokenType.AND)) {
            left = new And(left, parseComparison()).at(left);
        }
        return left;
    }

    private Expr parseComparison() {
        return parseBinary(this::parseAdd, TokenType.EQ, TokenType.NEQ, TokenType.LT, TokenType.GT, TokenType.GTEQ, TokenType.LTEQ);
    }

    private Expr parseAdd() {
        return parseBinary(this::parseMul, TokenType.PLUS, TokenType.MINUS);
    }

    private Expr parseMul() {
        return parseBinary(this::parseUnary, TokenType.MUL, TokenType.DIV, TokenType.MOD);
    }

    private interface ExprParser {
        Expr parse();
    }

    private Expr parseBinary(ExprParser operandParser, TokenType... types) {
        Expr left = operandParser.parse();
        while (matchAny(types)) {
            Token opToken = lastToken();
            TokenType op = opToken.getType();
            Expr right = operandParser.parse();
            left = switch (op) {
                case PLUS -> new Add(left, right).at(opToken);
                case MINUS -> new Sub(left, right).at(opToken);
                case MUL -> new Multiply(left, right).at(opToken);
                case DIV -> new Divide(left, right).at(opToken);
                case MOD -> new Modulo(left, right).at(opToken);
                case EQ -> new Equality(left, right).at(opToken);
                case NEQ -> new Inequality(left, right).at(opToken);
                case LT -> new Less(left, right).at(opToken);
                case GT -> new Greater(left, right).at(opToken);
                case GTEQ -> new GreaterEqual(left, right).at(opToken);
                case LTEQ -> new LessEqual(left, right).at(opToken);
                default -> throw BumpException.syntax(lastToken(), "Unsupported binary operator '" + op + "'.");
            };
        }
        return left;
    }

    private boolean matchAny(TokenType... types) {
        for (TokenType type : types) {
            if (match(type)) return true;
        }
        return false;
    }

    private Expr parseUnary() {
        if(match(TokenType.PLUSPLUS)) {
            Token operator = lastToken();
            return compoundStep(parseUnaryTarget(), operator, true, false);
        } else if(match(TokenType.MINUSMINUS)) {
            Token operator = lastToken();
            return compoundStep(parseUnaryTarget(), operator, false, false);
        } else if(match(TokenType.NOT)) {
            Token operator = lastToken();
            return new Not(parseUnary()).at(operator);
        } else if(match(TokenType.MINUS)) {
            Token operator = lastToken();
            return new Negative(parseUnary()).at(operator);
        }
        return parseCall();
    }

    private Expr parseCall() {
        Expr expr = parsePrimary();
        while (true) {
            if (check(TokenType.LT) && looksLikeCallTypeArguments()) {
                List<String> typeArguments = parseCallTypeArguments();
                expect(TokenType.LPAREN);
                expr = finishCall(expr, typeArguments);
            } else if (match(TokenType.LPAREN)) {
                expr = finishCall(expr, List.of());
            } else if (match(TokenType.DOT)) {
                Token name = expect(TokenType.ID);
                expr = new GetExpr(expr, name.getText()).at(name);
            } else if (match(TokenType.LBRACKET)) {
                Expr index = parseExpression();
                expect(TokenType.RBRACKET);
                expr = new ArrayIndex(expr, index).at(expr);
            } else if (match(TokenType.PLUSPLUS)) {
                expr = compoundStep(expr, lastToken(), true, true);
                break;
            } else if (match(TokenType.MINUSMINUS)) {
                expr = compoundStep(expr, lastToken(), false, true);
                break;
            } else {
                break;
            }
        }
        return expr;
    }

    private Expr finishCall(Expr callee, List<String> typeArguments) {
        List<Expr> arguments = new ArrayList<>();
        ensureNotAtEnd();
        if (!check(TokenType.RPAREN)) {
            do {
                arguments.add(parseExpression());
            } while (match(TokenType.COMMA));
        }
        expect(TokenType.RPAREN);
        return new CallExpr(callee, typeArguments, arguments).at(callee);
    }

    private List<String> parseCallTypeArguments() {
        expect(TokenType.LT);
        List<String> typeArguments = new ArrayList<>();
        do {
            typeArguments.add(parseTypeName());
        } while (match(TokenType.COMMA));
        expect(TokenType.GT);
        return typeArguments;
    }

    private Expr parsePrimary() {
        if (match(TokenType.INTEGER)) {
            Token integer = lastToken();
            try {
                return new IntegerLiteral(Integer.parseInt(integer.getText())).at(integer);
            } catch (NumberFormatException error) {
                throw BumpException.syntax(integer, "Integer literal '" + integer.getText() + "' is out of range.");
            }
        }
        if (match(TokenType.FLOAT)) {
            Token floating = lastToken();
            try {
                return new FloatLiteral(Double.parseDouble(floating.getText())).at(floating);
            } catch (NumberFormatException error) {
                throw BumpException.syntax(floating, "Float literal '" + floating.getText() + "' is out of range.");
            }
        }
        if (match(TokenType.SUPER)) {
            Token keyword = lastToken();
            expect(TokenType.DOT);
            Token member = expect(TokenType.ID);
            return new SuperExpr(keyword, member).at(keyword);
        }
        if (match(TokenType.STRING)) return new StringLiteral(lastToken().getText()).at(lastToken());
        if (match(TokenType.BOOLEAN)) return new BooleanLiteral(lastToken().getText().equals("true")).at(lastToken());
        if (match(TokenType.NULL)) return new NullLiteral().at(lastToken());
        if (match(TokenType.FUN)) {
            Token start = lastToken();
            String returnTypeName = parseTypeName();
            return parseFunctionBody(start, returnTypeName, false, List.of());
        }
        if (match(TokenType.ID)) {
            Token id = lastToken();
            if (check(TokenType.LT) && looksLikeTypeArguments()) {
                String fullTypeName = parseTypeNameRemainder(id.getText());
                return new VariableExpr(fullTypeName).at(id);
            }
            return new VariableExpr(id.getText()).at(id);
        }
        if (check(TokenType.LPAREN) && looksLikeLambdaLiteral()) {
            return parseLambdaLiteral();
        }
        if (match(TokenType.LPAREN)) {
            Expr expr = parseExpression();
            expect(TokenType.RPAREN);
            return expr;
        }

        if (match(TokenType.LBRACKET)) {
            return parseArrayLiteral(lastToken());
        }
        if (match(TokenType.LBRACE)) {
            return parseMapLiteral(lastToken());
        }

        throw error(peek(), "Unexpected token '" + (peek() != null ? peek().getText() : "EOF") + "'.");
    }

    private FunctionLiteralExpr parseLambdaLiteral() {
        Token start = expect(TokenType.LPAREN);
        functionDepth++;
        try {
            List<Parameter> parameters = parseParameters();
            expect(TokenType.RPAREN);
            expect(TokenType.FATARROW);

            List<Stmt> body;
            if (match(TokenType.LBRACE)) {
                body = parseBlock();
            } else {
                Expr value = parseExpression();
                body = List.of(new ReturnStmt(value).at(value));
            }
            return new FunctionLiteralExpr(null, List.of(), parameters, body, false).at(start);
        } finally {
            functionDepth--;
        }
    }

    private ArrayLiteral parseArrayLiteral(Token start) {
        List<Expr> values = new ArrayList<>();
        ensureNotAtEnd();
        if (!check(TokenType.RBRACKET)) {
            do {
                values.add(parseExpression());
            } while (match(TokenType.COMMA));
        }
        expect(TokenType.RBRACKET);

        return new ArrayLiteral(values).at(start);
    }

    private MapLiteral parseMapLiteral(Token start) {
        List<Expr> keys = new ArrayList<>();
        List<Expr> values = new ArrayList<>();
        ensureNotAtEnd();
        if (!check(TokenType.RBRACE)) {
            do {
                Expr key = parseExpression();
                expect(TokenType.COLON);
                Expr value = parseExpression();
                keys.add(key);
                values.add(value);
            } while (match(TokenType.COMMA));
        }
        expect(TokenType.RBRACE);
        return new MapLiteral(keys, values).at(start);
    }

    private BumpException error(Token token, String message) {
        return BumpException.syntax(token, message);
    }

    private Expr parseWithParens() {
        expect(TokenType.LPAREN);
        Expr expr = parseExpression();
        expect(TokenType.RPAREN);
        return expr;
    }

    private BlockStmt parseRequiredBlock(String message) {
        if (!match(TokenType.LBRACE)) {
            throw error(peek(), message);
        }
        return new BlockStmt(parseBlock()).at(lastToken());
    }

    private Expr parseUnaryTarget() {
        Expr target = parseCall();
        ensureAssignableTarget(target, lastToken(), "Invalid increment/decrement target.");
        return target;
    }

    private void parseClassMember(Token className, List<VariableDeclarationStmt> fields, List<VariableDeclarationStmt> methods, boolean classIsAbstract) {
        boolean isPrivate = false;
        if (match(TokenType.PRIVATE)) {
            isPrivate = true;
            if (match(TokenType.PUBLIC)) {
                throw error(lastToken(), "A class member cannot be both private and public.");
            }
        } else if (match(TokenType.PUBLIC)) {
            if (match(TokenType.PRIVATE)) {
                throw error(lastToken(), "A class member cannot be both public and private.");
            }
        }

        if (match(TokenType.ABSTRACT)) {
            Token abstractKeyword = lastToken();
            if (!classIsAbstract) {
                throw error(abstractKeyword, "Only abstract classes can declare abstract methods.");
            }
            if (!match(TokenType.FUN)) {
                throw error(peek(), "Expected 'fun' after 'abstract' in class.");
            }
            methods.add(parseAbstractMethodDeclaration(lastToken(), isPrivate));
            return;
        }

        if (looksLikeTypedDeclaration()) {
            fields.add(parseFieldDeclaration(className, isPrivate));
            return;
        }
        if (match(TokenType.FUN)) {
            methods.add((VariableDeclarationStmt) parseFunctionDeclaration(isPrivate));
            return;
        }
        throw error(peek(), "Expected method definition in class.");
    }

    private VariableDeclarationStmt parseFieldDeclaration(Token className, boolean isPrivate) {
        return parseTypedVariableDeclaration(isPrivate);
    }

    private VariableDeclarationStmt parseAbstractMethodDeclaration(Token keyword, boolean isPrivate) {
        String returnTypeName = parseTypeName();
        Token name = expect(TokenType.ID);
        List<TypeParameter> typeParameters = parseTypeParameters();
        expect(TokenType.LPAREN);
        List<Parameter> parameters = parseParameters();
        expect(TokenType.RPAREN);
        expect(TokenType.SEMICOLON);
        FunctionLiteralExpr function = new FunctionLiteralExpr(returnTypeName, typeParameters, parameters, List.of(), true).at(name);
        return new VariableDeclarationStmt("Function", name.getText(), function, isPrivate).at(name);
    }

    private List<TypeParameter> parseTypeParameters() {
        if (!match(TokenType.LT)) {
            return List.of();
        }
        List<TypeParameter> typeParameters = new ArrayList<>();
        do {
            Token nameToken = expect(TokenType.ID);
            String boundName = null;
            if (match(TokenType.EXTENDS) || match(TokenType.IMPLEMENTS)) {
                boundName = parseTypeName();
            }
            typeParameters.add(typeParameter(nameToken, boundName));
        } while (match(TokenType.COMMA));
        expect(TokenType.GT);
        return typeParameters;
    }

    private List<Parameter> parseParameters() {
        List<Parameter> parameters = new ArrayList<>();
        ensureNotAtEnd();
        if (!check(TokenType.RPAREN)) {
            do {
                Token start = expect(TokenType.ID);
                String typeName = parseTypeNameRemainder(start.getText());
                Token nameToken = expect(TokenType.ID);
                parameters.add(parameter(typeName, start, nameToken));
            } while (match(TokenType.COMMA));
        }
        return parameters;
    }

    private Parameter parameter(String typeName, Token locationToken, Token nameToken) {
        return new Parameter(
                typeName,
                nameToken.getText(),
                locationToken.getLine(),
                locationToken.getColumn(),
                locationToken.getSourceLength()
        );
    }

    private TypeParameter typeParameter(Token nameToken, String boundName) {
        return new TypeParameter(
                nameToken.getText(),
                boundName,
                nameToken.getLine(),
                nameToken.getColumn(),
                nameToken.getSourceLength()
        );
    }

    private void ensureNotAtEnd() {
        if (cursor.isAtEnd()) {
            throw BumpException.syntax(lastToken(), "Unexpected end of file.");
        }
    }

    boolean check(TokenType type) {
        return peek() != null && peek().getType() == type;
    }

    private Expr compoundStep(Expr target, Token operator, boolean increment, boolean returnsPreviousValue) {
        return new CompoundSetExpr(
                target,
                increment ? TokenType.PLUSASSIGN : TokenType.MINUSASSIGN,
                new IntegerLiteral(1).at(operator),
                returnsPreviousValue
        ).at(target);
    }

    private void ensureAssignableTarget(Expr target, Token token, String message) {
        if (!(target instanceof VariableExpr || target instanceof GetExpr || target instanceof ArrayIndex)) {
            throw error(token, message);
        }
    }

    private Token lastToken() {
        return cursor.previous();
    }

    Token peek() {
        return cursor.current();
    }

    Token peek(int offset) {
        return cursor.peek(offset);
    }

    private Token advance() {
        return cursor.advance();
    }

    private boolean match(TokenType type) {
        return cursor.match(type);
    }

    private boolean looksLikeTypedDeclaration() {
        return lookahead.looksLikeTypedDeclaration();
    }

    private boolean looksLikeEnhancedForHeader() {
        return lookahead.looksLikeEnhancedForHeader();
    }

    private String parseTypeName() {
        if (match(TokenType.VOID)) {
            return "Null";
        }
        Token first = expect(TokenType.ID);
        return parseTypeNameRemainder(first.getText());
    }

    private String parseTypeNameRemainder(String baseName) {
        StringBuilder builder = new StringBuilder(baseName);
        if (!match(TokenType.LT)) {
            return builder.toString();
        }
        builder.append('<');
        while (true) {
            builder.append(parseTypeName());
            if (!match(TokenType.COMMA)) {
                break;
            }
            builder.append(',');
        }
        expect(TokenType.GT);
        builder.append('>');
        return builder.toString();
    }

    private boolean looksLikeTypeArguments() {
        return lookahead.looksLikeTypeArguments();
    }

    private boolean looksLikeCallTypeArguments() {
        return lookahead.looksLikeCallTypeArguments();
    }

    private boolean looksLikeLambdaLiteral() {
        return lookahead.looksLikeLambdaLiteral();
    }

    private Token expect(TokenType type) {
        return cursor.expect(type);
    }
}
