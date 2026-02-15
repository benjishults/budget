# untested!

given_filename="$1"
if [ -z "$given_filename" ]
then
  echo "no filename given"
  backups="$(ls -l "$BPS_BUDGET_DATA_DIR"/backup/postgres-*)"
  backup_count="$(echo "$backups" | wc -l)"
  if [ "$backup_count" -lt 1 ]
  then
    echo "no backups found to restore"
  else
    echo "restoring from most recent backups"
    filenames_file=$(mktemp)
    sorted_filenames_file=$(mktemp)
    echo "$backups" | while read -r backup
    do
      prefix=${backup%????????????????????????}
      filename="${backup#"$prefix"}"
      echo "$filename" >> "$filenames_file"
    done
    sort "$filenames_file" > "$sorted_filenames_file"
    printf "most recent backup\n%s\n" "$(tail --lines=1 "$sorted_filenames_file")"
    restore_file="${BPS_BUDGET_DATA_DIR}/backup/$(tail --lines=1 "$sorted_filenames_file")"
    echo -n "Are you sure you want to restore $restore_file? [y/N] "
    read -r response
    if [ "$response" = 'y' ]
    then
      pg_dump -U admin -h localhost budget < "$restore_file"
      echo "restored"
    else
      echo "not restoring"
    fi
  fi
else
  echo -n "Are you sure you want to restore $given_filename? [y/N] "
  read -r response
  if [ "$response" = 'y' ]
  then
    pg_dump -U admin -h localhost budget < "$given_filename"
    echo "restored"
  else
    echo "not restoring"
  fi
fi



