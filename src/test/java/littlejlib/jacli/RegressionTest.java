package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.core.RefKind;
import littlejlib.jacli.query.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * One test per defect a field tester found in already-shipped code. Every assertion here failed at
 * some point in a build that was believed to be working, which is the only reason to keep them.
 */
class RegressionTest {

    static final Opts OPTS = Opts.defaults();

    @Test
    @DisplayName("api on an interface lists its members (implied public modifiers were being stripped)")
    void apiOnInterface() {
        var answer = Discover.api(Fixture.store(), "fx.Shape", null, false, OPTS);
        assertEquals(Answer.OK, answer.exit(), answer.text());
        assertTrue(answer.text().contains("area()"), answer.text());
        assertTrue(answer.text().contains("scaled"), answer.text());
    }

    @Test
    @DisplayName("api on a lower-case package scope is not parsed as a member spec")
    void apiOnPackageScope() {
        var answer = Discover.api(Fixture.store(), "fx", null, false, OPTS);
        assertEquals(Answer.OK, answer.exit(), answer.text());
        assertTrue(answer.text().contains("Shape"), answer.text());
        assertFalse(answer.text().contains("no declarations"), answer.text());
    }

    @Test
    @DisplayName("--overrides does not trip the ambiguity guard on a method that has overrides")
    void overridesIsNotAmbiguity() {
        var spec = "fx.Shape#scaled(double)";
        var plain = Verbs.refs(Fixture.store(), spec, OPTS);
        var widened = Verbs.refs(Fixture.store(), spec, OPTS.withOverrides(true));
        assertEquals(Answer.OK, plain.exit(), plain.text());
        assertEquals(Answer.OK, widened.exit(), "--overrides must not exit AMBIGUOUS: " + widened.text());
        assertNotEquals(Answer.AMBIGUOUS, widened.exit());
    }

    @Test
    @DisplayName("callers --overrides likewise succeeds where the method is genuinely overridden")
    void callersWithOverrides() {
        var answer = Graph.callers(Fixture.store(), "fx.Shape#scaled(double)", OPTS.withOverrides(true));
        assertEquals(Answer.OK, answer.exit(), answer.text());
    }

    @Test
    @DisplayName("a genuinely ambiguous spec on a usage verb still exits 3 with candidates")
    void genuineAmbiguityStillGuarded() {
        var answer = Verbs.refs(Fixture.store(), "scaled", OPTS);
        assertEquals(Answer.AMBIGUOUS, answer.exit(), answer.text());
        assertTrue(answer.text().contains("scaled"), answer.text());
    }

    @Test
    @DisplayName("def lists every declaration of an ambiguous name and exits 0")
    void defListsAllAndSucceeds() {
        var answer = Verbs.def(Fixture.store(), "scaled", OPTS);
        assertEquals(Answer.OK, answer.exit(), answer.text());
    }

    @Test
    @DisplayName("a --kind filter matching nothing reports other kinds rather than claiming no use")
    void kindFilterDoesNotClaimUnused() {
        var answer = Verbs.refs(Fixture.store(), "fx.Shape", OPTS.withKinds(Set.of(RefKind.NEW)));
        assertFalse(answer.text().contains("never used"),
                "an empty kind filter must not assert the symbol is unused: " + answer.text());
        assertTrue(answer.text().contains("references of other kinds"), answer.text());
    }

    @Test
    @DisplayName("impls finds an indirect implementer that never names the interface")
    void implsIsTransitive() {
        var answer = Graph.impls(Fixture.store(), "fx.Shape", OPTS);
        assertEquals(Answer.OK, answer.exit(), answer.text());
        assertTrue(answer.text().contains("AbstractShape"), answer.text());
        assertTrue(answer.text().contains("Circle"), "Circle reaches Shape only through AbstractShape: "
                + answer.text());
    }

    @Test
    @DisplayName("a generic bound is a TYPE use, not an EXTENDS")
    void genericBoundIsTypeNotExtends() {
        var store = Fixture.store();
        var asExtends = Verbs.refs(store, "fx.Shape", OPTS.withKinds(Set.of(RefKind.EXTENDS)));
        var asImplements = Verbs.refs(store, "fx.Shape", OPTS.withKinds(Set.of(RefKind.IMPLEMENTS)));
        assertFalse(asExtends.text().contains("totalArea"),
                "<T extends Shape> must not be classified EXTENDS: " + asExtends.text());
        assertTrue(asImplements.text().contains("AbstractShape"), asImplements.text());
    }

    @Test
    @DisplayName("overloads are distinguished, so one overload's references exclude the other's")
    void overloadsAreDistinct() {
        var store = Fixture.store();
        var doubleArg = Verbs.refs(store, "fx.Shape#scaled(double)", OPTS);
        var intArg = Verbs.refs(store, "fx.Shape#scaled(int)", OPTS);
        assertEquals(Answer.OK, doubleArg.exit(), doubleArg.text());
        assertNotEquals(doubleArg.text(), intArg.text(), "the two overloads must not answer identically");
    }

    @Test
    @DisplayName("unused finds a public method nothing references")
    void unusedFindsOrphan() {
        var answer = Graph.unused(Fixture.store(), OPTS);
        assertTrue(answer.text().contains("orphan"), answer.text());
    }

    @Test
    @DisplayName("src slices a method body from source")
    void srcSlicesBody() {
        var answer = Verbs.src(Fixture.store(), "fx.Circle#area", OPTS);
        assertEquals(Answer.OK, answer.exit(), answer.text());
        assertTrue(answer.text().contains("Math.PI"), answer.text());
    }

    @Test
    @DisplayName("src accepts the dotted form a stack trace prints")
    void srcAcceptsStackTraceForm() {
        var answer = Verbs.src(Fixture.store(), "fx.Circle.area", OPTS);
        assertEquals(Answer.OK, answer.exit(), answer.text());
        assertTrue(answer.text().contains("Math.PI"), answer.text());
    }

    @Test
    @DisplayName("a name that is neither declared nor referenced exits 1, not 0")
    void unknownNameExitsNothingFound() {
        var answer = Verbs.refs(Fixture.store(), "NoSuchTypeAnywhere", OPTS);
        assertEquals(Answer.NOTHING_FOUND, answer.exit(), answer.text());
    }

    @Test
    @DisplayName("a record's implicit accessors are listed - they ARE its public read surface")
    void recordAccessorsAreVisible() {
        var answer = Discover.api(Fixture.store(), "fx.Outer.Point", null, false, OPTS);
        assertEquals(Answer.OK, answer.exit(), answer.text());
        assertTrue(answer.text().contains("x()"), "implicit accessor x() must appear: " + answer.text());
        assertTrue(answer.text().contains("y()"), answer.text());
    }

    @Test
    @DisplayName("a call to an implicit record accessor is reported, not silently under-counted")
    void callToImplicitAccessorIsFound() {
        var answer = Verbs.refs(Fixture.store(), "fx.Outer.Point#x", OPTS.withAll(true));
        assertEquals(Answer.OK, answer.exit(), answer.text());
        assertTrue(answer.text().contains("firstCoordinate"),
                "p.x() in firstCoordinate must be reported: " + answer.text());
    }

    @Test
    @DisplayName("an enum's implicit values() and valueOf() are reachable")
    void enumImplicitMembers() {
        var answer = Discover.api(Fixture.store(), "fx.Kind", null, false, OPTS);
        assertTrue(answer.text().contains("values()"), answer.text());
        assertTrue(answer.text().contains("valueOf"), answer.text());
    }

    @Test
    @DisplayName("src on an implicit member says there is no source rather than showing the wrong lines")
    void srcOnImplicitMember() {
        var answer = Verbs.src(Fixture.store(), "fx.Outer.Point#x", OPTS);
        assertTrue(answer.text().contains("generated by the compiler"), answer.text());
    }

    @Test
    @DisplayName("--field and --method separate a field from a same-named method")
    void fieldAndMethodAreSeparable() {
        var store = Fixture.store();
        var ambiguous = Verbs.refs(store, "fx.Kind#label", OPTS);
        assertEquals(Answer.AMBIGUOUS, ambiguous.exit(), ambiguous.text());
        assertTrue(ambiguous.text().contains("--field"), "the remedy must be usable: " + ambiguous.text());
        assertTrue(ambiguous.text().contains("--all"), ambiguous.text());

        var asField = Verbs.refs(store, "fx.Kind#label", OPTS.withMember(Opts.Member.FIELD));
        var asMethod = Verbs.refs(store, "fx.Kind#label", OPTS.withMember(Opts.Member.METHOD));
        assertNotEquals(Answer.AMBIGUOUS, asField.exit(), asField.text());
        assertNotEquals(Answer.AMBIGUOUS, asMethod.exit(), asMethod.text());
    }

    @Test
    @DisplayName("api on a real but empty type says so, and names where its API is inherited from")
    void emptyTypeIsNotNonexistent() {
        var empty = Discover.api(Fixture.store(), "fx.Marker", null, false, OPTS);
        assertFalse(empty.text().contains("nothing named"),
                "a real empty interface must not be reported as nonexistent: " + empty.text());
        assertTrue(empty.text().contains("inherited") || empty.text().contains("Shape"), empty.text());

        var absent = Discover.api(Fixture.store(), "fx.NoSuchTypeAtAll", null, false, OPTS);
        assertTrue(absent.text().contains("nothing named"), absent.text());
    }

    @Test
    @DisplayName("tree includes public nested types, shown as Outer.Inner")
    void treeIncludesNestedTypes() {
        var answer = Discover.tree(Fixture.store(), null);
        assertEquals(Answer.OK, answer.exit(), answer.text());
        assertTrue(answer.text().contains("Outer.Inner"), "nested types must be discoverable: " + answer.text());
        assertTrue(answer.text().contains("Outer.Point"), answer.text());
    }

    @Test
    @DisplayName("a parenthesised spec never matches a field - it fired on every record")
    void parenthesesMeanMethod() {
        var store = Fixture.store();
        var withParens = Verbs.refs(store, "fx.Outer.Point#x()", OPTS);
        assertNotEquals(Answer.AMBIGUOUS, withParens.exit(),
                "'#x()' must not also match the component field: " + withParens.text());
        assertFalse(withParens.text().contains("[field]"), withParens.text());
    }

    @Test
    @DisplayName("an implicit accessor points at its component, not at the record header")
    void accessorPointsAtItsComponent() {
        var point = Fixture.store().declsByName("y").stream()
                .filter(d -> d.implicit() && d.kind().isExecutable()).findFirst().orElseThrow();
        var header = Fixture.store().declsByName("Point").stream().findFirst().orElseThrow();
        assertNotEquals(header.line(), point.line(),
                "accessor y() should not report the record header's line " + header.line());
    }

    @Test
    @DisplayName("api --inherited shows what a marker interface actually offers")
    void inheritedApi() {
        var answer = Discover.inherited(Fixture.store(), "fx.Marker", null, false, OPTS);
        assertEquals(Answer.OK, answer.exit(), answer.text());
        assertTrue(answer.text().contains("area()"), "Marker's API is Shape's: " + answer.text());
        assertTrue(answer.text().contains("inherited"), answer.text());
    }

    @Test
    @DisplayName("--private widens api on a LOCAL project, not only on a bytecode target")
    void privateWidensLocalApi() {
        var store = Fixture.store();
        var publicOnly = Discover.api(store, "fx.Circle", null, false, OPTS);
        var withPrivate = Discover.api(store, "fx.Circle", null, false, OPTS.withNonPublic(true));
        /* Match the FIELD's signature, not the substring "double radius", which also occurs inside the
         * constructor's parameter list - the first version of this test asserted on that and failed
         * against correct behaviour. */
        assertTrue(withPrivate.text().contains("final double radius"),
                "--private must reveal the package-private field: " + withPrivate.text());
        assertFalse(publicOnly.text().contains("final double radius"), publicOnly.text());
        assertTrue(withPrivate.text().length() > publicOnly.text().length(), withPrivate.text());
    }

    @Test
    @DisplayName("api --code prints each declaration's source, as symbols gen -c did")
    void apiWithCode() {
        var answer = Discover.api(Fixture.store(), "fx.Shapes", "orphan", false, OPTS.withCode(true));
        assertEquals(Answer.OK, answer.exit(), answer.text());
        assertTrue(answer.text().contains("nobody calls me"),
                "the body should be included with --code: " + answer.text());
    }

    @Test
    @DisplayName("nested types, records and enums are all reachable")
    void nestedRecordAndEnum() {
        var store = Fixture.store();
        assertEquals(Answer.OK, Verbs.def(store, "fx.Outer.Point", OPTS).exit());
        assertEquals(Answer.OK, Verbs.def(store, "fx.Kind", OPTS).exit());
        assertEquals(Answer.OK, Verbs.def(store, "fx.Outer.Inner", OPTS).exit());
    }

    @Test
    @DisplayName("api --inherited on an unknown type says the option was ignored, rather than answering as plain api")
    void inheritedSaysWhenItWasIgnored() {
        var answer = Discover.inherited(Fixture.store(), "fx.NoSuchTypeAtAll", null, false, OPTS);
        assertTrue(answer.text().contains("--inherited was ignored"),
                "a silent fallback leaves an empty answer reading as 'inherits nothing': " + answer.text());
        assertTrue(answer.text().contains("NoSuchTypeAtAll"), answer.text());
    }

    @Test
    @DisplayName("api --inherited with only a name filter says why it could not walk a hierarchy")
    void inheritedNeedsAType() {
        var answer = Discover.inherited(Fixture.store(), null, "area", false, OPTS);
        assertTrue(answer.text().contains("--inherited"), answer.text());
        assertTrue(answer.text().contains("needs a type"), answer.text());
    }

    /**
     * Asserted in the positive, on types whose counts really are one: the first version of this test
     * used fx.Marker, whose hierarchy is two types, so "1 types" could never have appeared and the
     * assertion held whether or not the code was fixed.
     */
    @Test
    @DisplayName("counts of one are not reported in the plural")
    void countsOfOneReadCorrectly() {
        var inherited = Discover.inherited(Fixture.store(), "fx.Shape", null, false, OPTS);
        assertTrue(inherited.text().contains("1 type in the hierarchy"), inherited.text());
        var tree = Discover.tree(Fixture.store(), "fx");
        assertTrue(tree.text().contains("1 package,"), tree.text());
    }

    @Test
    @DisplayName("a project with no build file of its own is recognisable as such")
    void unknownCoordsAreRecognisable() {
        assertTrue(littlejlib.jacli.proj.Coords.isUnknown("unknown:unknown:0"));
        assertTrue(littlejlib.jacli.proj.Coords.isUnknown(null));
        assertFalse(littlejlib.jacli.proj.Coords.isUnknown("io.github.luvml:luvml:2.0"));
    }
}
