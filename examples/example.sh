#!/bin/bash

# See usage information for a description.
#
# The default output format corresponds to a Markdown table and can be
# interpreted using `pandoc` (https://pandoc.org/MANUAL.html#tables).

set -eu

# ------------------------------------------------------------ GLOBAL VARIABLES

readonly SCRIPTS_DIR=$(dirname "$(readlink -f "$0")")

# Location containing integration tests.
readonly INTEGRATION_DIR="${INTEGRATION_DIR:-$SCRIPTS_DIR/../test/integration}"

# Directory used to store integration test outputs.
readonly OUTPUT_DIR="${OUTPUT_DIR:-integration_tests}"

# mc compiler binary
readonly MCC="${MCC:-./mcc}"

# colour support
if [[ -t 1 ]]; then
	readonly NC='\e[0m'
	readonly Red='\e[1;31m'
	readonly Green='\e[1;32m'
else
	readonly NC=''
	readonly Red=''
	readonly Green=''
fi

# Pattern used to collect test inputs.
pattern="*"

# Options:
option_csv=false
option_valgrind=false

# ------------------------------------------------------------------- Functions

run_compiler()
{
	local test=$1
	local input="$INTEGRATION_DIR/$test/$test.mc"
	local output="$OUTPUT_DIR/$test"
	local stats="$OUTPUT_DIR/$test.mcc.stats.txt"
	local mcc_output="$OUTPUT_DIR/$test.mcc.output.txt"

	local valgrind=''
	if $option_valgrind; then
		valgrind='valgrind --error-exitcode=1 --leak-check=full'
	fi

	command time \
		--format "%e %M %x" \
		--output "$stats" \
		$valgrind "$MCC" \
			-o "$output" \
			"$input" \
			&> "$mcc_output" \
	|| return 1

	tail -n1 "$stats"
}

run_integration_test()
{
	local test=$1
	local stdin="$INTEGRATION_DIR/$test/$test.stdin.txt"
	local ex_stdout="$INTEGRATION_DIR/$test/$test.stdout.txt"
	local ac_stdout="$OUTPUT_DIR/$test.stdout.txt"
	local diff_stdout="$OUTPUT_DIR/$test.stdout.diff"
	local stats="$OUTPUT_DIR/$test.stats.txt"

	[[ -e "$OUTPUT_DIR/$test" ]] || return 1

	command time \
		--format "%e %M %x" \
		--output "$stats" \
		"$OUTPUT_DIR/$test" \
			< "$stdin" \
			> "$ac_stdout" \
	|| return 1

	if ! diff -u "$ex_stdout" "$ac_stdout" > "$diff_stdout"; then
		return 1
	fi

	tail -n1 "$stats"
}

print_header_md()
{
	echo "Input                                         mcc Time     mcc Memory  mcc Status      exe Time     exe Memory  exe Status"
	echo "----------------------------------------- ------------ -------------- ------------ ------------ -------------- ------------"
}

print_header_csv()
{
	echo "Input,mcc Time [s],mcc Memory [kB],mcc Status,exe Time [s],exe Memory [kB],exe Status"
}

print_fancy_status()
{
	if [[ "$1" == "0" ]]; then
		echo -en "${Green}[ Ok ]${NC}"
	else
		echo -en "${Red}[Fail]${NC}"
	fi
}

print_run_md()
{
	printf "%-40s %10s s  %10s kB     " "$1" "$2" "$3"
	print_fancy_status "$4"
	printf "   %10s s  %10s kB     " "$5" "$6"
	print_fancy_status "$7"
	printf "\\n"
}

print_run_csv()
{
	echo "$@" | tr ' ' ','
}

print_run()
{
	if $option_csv; then
		print_run_csv "$@"
	else
		print_run_md "$@"
	fi
}

print_header()
{
	if $option_csv; then
		print_header_csv
	else
		print_header_md
	fi
}

print_usage()
{
	echo "usage: $0 [OPTIONS] [PATTERN]"
	echo
	echo "Runs integration tests matching the given PATTERN using the mC"
	echo "compiler. If PATTERN is omitted, all integrations are run."
	echo
	echo "OPTIONS:"
	echo "  -h, --help       displays this help message"
	echo "  -c, --csv        output as CSV"
	echo "  -v, --valgrind   run compiler using valgrind"
	echo
	echo "Environment Variables:"
	echo "  MCC                  override the MCC executable path (defaults to ./mcc)"
	echo "  INTEGRATION_DIR      override path to the integration test directory"
	echo "  OUTPUT_DIR           override path to the directory storing outputs"
	echo
}

assert_installed()
{
	if ! hash "$1" &> /dev/null; then
		echo >&2 "$1 not installed"
		exit 1
	fi
}

check_prerequisites()
{
	assert_installed time

	mkdir -p "$OUTPUT_DIR"
}

parse_args()
{
	ARGS=$(getopt -o hcv -l help,csv,valgrind -- "$@")
	eval set -- "$ARGS"

	while true; do
		case "$1" in
			-h|--help)
				print_usage
				exit
				;;

			-c|--csv)
				option_csv=true
				shift
				;;

			-v|--valgrind)
				option_valgrind=true
				shift
				;;

			--)
				shift
				break
				;;

			*)
				exit 1
				;;
		esac
	done

	if [[ -n ${1+x} ]]; then
		pattern="$1"
	fi
}

# ------------------------------------------------------------------------ Main

parse_args "$@"

# Clean previous runs
rm -rf "$OUTPUT_DIR"

check_prerequisites

print_header

(cd "$INTEGRATION_DIR"; find . -mindepth 1 -type d -name "${pattern}" -print0) | sort -z |
(
	flawless=true
	while read -r -d $'\0' test; do

		# Compile integration test
		if ! mcc_result=$(run_compiler "$test"); then
			mcc_result="- - 1"
		fi

		if [[ $(echo $mcc_result | cut -d ' ' -f3) -ne 0 ]]; then
			flawless=false
		fi

		# Run integration test
		if ! exe_result=$(run_integration_test "$test"); then
			exe_result="- - 1"
		fi

		if [[ $(echo $exe_result | cut -d ' ' -f3) -ne 0 ]]; then
			flawless=false
		fi

		print_run "$test" $mcc_result $exe_result

	done
	$flawless
)
