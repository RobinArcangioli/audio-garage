#!/usr/bin/env bash
# Prints, for each .m4a in $1: major brand, codec, profile, sample rate, channels,
# declared duration, measured bitrate, whether the MP4 is fragmented (mvex box),
# and a seek check (decode 2 s starting at 80 % of the duration).
set -euo pipefail
dir="${1:?usage: inspect-m4a.sh <dir>}"
printf 'file\tbrand\tcodec\tprofile\tsample_rate\tchannels\tduration_s\tbitrate_kbps\tfragmented\tseek_80pct\n'
shopt -s nullglob
for f in "$dir"/*.m4a; do
  if ! json=$(ffprobe -v error -print_format json -show_format -show_streams "$f" 2>/dev/null) \
     || [ "$(jq -r '.streams | length' <<<"$json")" = 0 ]; then
    printf '%s\tUNREADABLE\n' "$(basename "$f")"
    continue
  fi
  q() { jq -r "$1" <<<"$json"; }
  dur=$(q '.format.duration')
  kbps=$(q '.format.bit_rate | tonumber / 1000 | round')
  # Fragmented MP4 (DASH-style) declares an 'mvex' box inside 'moov' (ISO/IEC 14496-12);
  # ffprobe's trace lists the boxes it parses (it stops before any 'moof').
  trace=$(ffprobe -v trace "$f" 2>&1 || true)
  if grep -q "type:'mvex'" <<<"$trace"; then frag=yes; else frag=no; fi
  seek_at=$(awk -v d="$dur" 'BEGIN { printf "%.2f", d * 0.8 }')
  if ffmpeg -v error -xerror -ss "$seek_at" -i "$f" -t 2 -f null - >/dev/null 2>&1; then seek=ok; else seek=FAIL; fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%.2f\t%s\t%s\t%s\n' "$(basename "$f")" \
    "$(q '.format.tags.major_brand // "?"')" "$(q '.streams[0].codec_name')" "$(q '.streams[0].profile // "?"')" \
    "$(q '.streams[0].sample_rate')" "$(q '.streams[0].channels')" "$dur" "$kbps" "$frag" "$seek"
done
