#!/bin/bash

git checkout dev
git pull origin dev

git checkout prd
git pull origin prd

git merge dev
git push origin prd

TAG="release_$(date +'%Y%m%d_%H%M%S')"
git tag -a $TAG -m "Release on $(date)"

git push origin $TAG