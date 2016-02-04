#! /bin/bash

lastmod=$(git log --date=iso8601-strict --pretty=format:"%ad" -1 HEAD -- "${1}")
date=$(git log --diff-filter=A --date=iso8601-strict --pretty=format:"%ad" -1 HEAD -- "${1}")
sed "s/^\([ ]*date[ ]*=[ ]*\)\"\"\([ ]*\)$/\1\"${date}\"\2/g" | sed "s/^\([ ]*lastmod[ ]*=[ ]*\)\"\"\([ ]*\)$/\1\"${lastmod}\"\2/g"
