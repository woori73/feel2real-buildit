# Sourced by deploy script; no top-level mutations or output.
f2r_counseling_model_apply() {
  local model_mode="$1" model_env="$2" model_key_file="${3:-}" model_key="" model_tmp
  case "$model_mode" in
    unchanged) return 0 ;;
    luna)
      test -r "$model_key_file" || return 2
      model_key=$(cat "$model_key_file")
      [[ "$model_key" =~ ^sk-[A-Za-z0-9_-]+$ ]] || return 2
      ;;
    meta) ;;
    *) return 2 ;;
  esac
  test -f "$model_env" || return 2
  model_tmp=$(mktemp "${model_env}.luna.XXXXXX") || return 2
  chmod 600 "$model_tmp"
  if ! awk -v mode="$model_mode" '
    !/^(APP_ADMISSIONS_LLM_PRIMARY|NEWSTART_LLM_PRIMARY|APP_NEWSTART_LLM_PRIMARY)=/ &&
    !(mode == "luna" && /^OPENAI_API_KEY=/) { print }
  ' "$model_env" > "$model_tmp"; then rm -f "$model_tmp"; return 2; fi
  if [ "$model_mode" = luna ]; then
    printf 'OPENAI_API_KEY=%s\nAPP_ADMISSIONS_LLM_PRIMARY=openai\nNEWSTART_LLM_PRIMARY=openai\nAPP_NEWSTART_LLM_PRIMARY=openai\n' "$model_key" >> "$model_tmp"
  else
    printf 'APP_ADMISSIONS_LLM_PRIMARY=meta\nNEWSTART_LLM_PRIMARY=meta\nAPP_NEWSTART_LLM_PRIMARY=meta\n' >> "$model_tmp"
  fi
  unset model_key
  mv "$model_tmp" "$model_env"
}
