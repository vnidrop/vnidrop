#!/usr/bin/env bash

set -euo pipefail

version=${1:-1.0.0}

if [[ ${GITHUB_REF_TYPE:-} == "tag" ]]; then
	if [[ ! ${GITHUB_REF_NAME:-} =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
		echo "Linux release tags must use vMAJOR.MINOR.PATCH" >&2
		exit 1
	fi
	version=${GITHUB_REF_NAME#v}
fi

if [[ ! $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
	echo "Version must use MAJOR.MINOR.PATCH" >&2
	exit 1
fi

IFS=. read -r major minor patch <<< "$version"
parts=("$major" "$minor" "$patch")

for index in "${!parts[@]}"; do
	part=${parts[$index]}
	if [[ $part != "0" && $part == 0* ]]; then
		echo "Version components must be canonical integers without leading zeroes" >&2
		exit 1
	fi
	if (( ${#part} > 5 )) || (( 10#$part > 65535 )); then
		echo "Version components must be between 0 and 65535" >&2
		exit 1
	fi
	if (( index == 0 && 10#$part == 0 )); then
		echo "The major version must be non-zero" >&2
		exit 1
	fi
done

printf '%s\n' "$version"
