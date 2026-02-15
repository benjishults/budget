pg_dump -U admin -h localhost budget > "$BPS_BUDGET_DATA_DIR"/backup/postgres-"$(date +"%011s")".sql

backups="$(ls -l "$BPS_BUDGET_DATA_DIR"/backup/postgres-*)"
backup_count="$(echo "$backups" | wc -l)"
echo "$backup_count backups"
printf "backups\n%s\n" "$backups"
if [ "$backup_count" -gt 1 ]
then
  echo "deleting old backups"
  filenames_file=$(mktemp)
  sorted_filenames_file=$(mktemp)
  echo "$backups" | while read -r backup
  do
    prefix=${backup%????????????????????????}
    filename="${backup#"$prefix"}"
    echo "$filename" >> "$filenames_file"
  done
  sort "$filenames_file" > "$sorted_filenames_file"
  printf "oldest backups\n%s\n" "$(head --lines=-10 "$sorted_filenames_file")"
  echo -n "Do you want to delete old backups? [y/N] "
  read -r response
  if [ "$response" = 'y' ]
  then
    head --lines=-10 "$sorted_filenames_file" | while read -r filename
    do
      echo "deleting old backup: ${BPS_BUDGET_DATA_DIR}/backup/$filename"
      rm "${BPS_BUDGET_DATA_DIR}/backup/$filename"
    done
  else
    echo "leaving old backups"
  fi
fi
