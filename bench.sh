#!/bin/sh
# Head-to-head measurement for PRP line 99: is jacli worth having over a plain text search?
# Reports, per real question: wall time, output bytes, output lines, and exit code.
# Correctness is judged by hand in the write-up; this script only measures cost.

L="C:/user/code/luvml/luvml"
S="C:/user/code/luvml/luvs"

run() {
    label="$1"; shift
    s=$(date +%s%N)
    out=$("$@" 2>&1)
    rc=$?
    e=$(date +%s%N)
    bytes=$(printf '%s' "$out" | wc -c | tr -d ' ')
    lines=$(printf '%s\n' "$out" | wc -l | tr -d ' ')
    printf '  %-26s %6sms %8s bytes %5s lines exit=%s\n' "$label" $(( (e-s)/1000000 )) "$bytes" "$lines" "$rc"
}

runsh() {
    label="$1"; shift
    s=$(date +%s%N)
    out=$(sh -c "$*" 2>&1)
    rc=$?
    e=$(date +%s%N)
    bytes=$(printf '%s' "$out" | wc -c | tr -d ' ')
    lines=$(printf '%s\n' "$out" | wc -l | tr -d ' ')
    printf '  %-26s %6sms %8s bytes %5s lines exit=%s\n' "$label" $(( (e-s)/1000000 )) "$bytes" "$lines" "$rc"
}

echo "Q1  who calls spaceSeparatedAttrVals"
runsh "grep" "grep -rn spaceSeparatedAttrVals $L/src"
run   "jacli" jacli refs "luvml.C#spaceSeparatedAttrVals" -p "$L"

echo "Q2  what implements Frag_I"
runsh "grep" "grep -rn Frag_I $L/src"
runsh "grep, filtered" "grep -rnE '(implements|extends)[^;{]*Frag_I' $L/src"
run   "jacli" jacli impls "luvx.Frag_I" --scope luvml -p "$L"

echo "Q3  what does luvs offer for flex"
runsh "grep" "grep -rni flex $S/src"
run   "jacli" jacli api --name flex -p "$S"

echo "Q4  uses of one overload of ba"
runsh "grep" "grep -rnw ba $L/src"
run   "jacli" jacli refs "luvml.o.Out_I#ba(byte[])" -p "$L"

echo "Q5  read a method body without opening the file"
runsh "grep+sed" "grep -n 'spaceSeparatedAttrVals' $L/src/main/java/luvml/C.java | head -1"
run   "jacli" jacli src "luvml.C#spaceSeparatedAttrVals" -p "$L"

echo "Q6  find one known identifier in one known file (grep should win)"
runsh "grep" "grep -n 'div(' $L/src/main/java/luvml/E.java"
run   "jacli" jacli refs "luvml.E#div" -p "$L" --all

echo "Q7  API of a dependency with no sources checked out"
runsh "grep" "grep -rn flex C:/Users/User/.m2/repository/io/github/luvml/luvs/2.0/"
run   "jacli" jacli api -T "io.github.luvml:luvs:2.0" --name flex
