import json, os, urllib.request
from concurrent.futures import ThreadPoolExecutor

os.makedirs('app/src/main/assets/audio/sudais', exist_ok=True)

def fetch_json(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'MushafAppBuilder/3.0'})
    with urllib.request.urlopen(req, timeout=45) as resp:
        return json.loads(resp.read().decode('utf-8'))

print("Downloading complete Uthmani Quran text (114 surahs / 604 pages)...")
uthmani = fetch_json('https://api.alquran.cloud/v1/quran/quran-uthmani')['data']['surahs']
print("Downloading Tafsir Al-Muyassar...")
tafsir = fetch_json('https://api.alquran.cloud/v1/quran/ar.muyassar')['data']['surahs']

merged = []
for idx, s in enumerate(uthmani):
    t_ayahs = tafsir[idx]['ayahs'] if idx < len(tafsir) else []
    ayahs = []
    for j, a in enumerate(s['ayahs']):
        ayahs.append({
            'number': a['number'],
            'numberInSurah': a['numberInSurah'],
            'juz': a['juz'],
            'page': a['page'],
            'text': a['text'],
            'tafsir': t_ayahs[j]['text'] if j < len(t_ayahs) else ''
        })
    merged.append({
        'number': s['number'],
        'name': s['name'],
        'englishName': s.get('englishName', ''),
        'revelationType': s.get('revelationType', 'Meccan'),
        'ayahs': ayahs
    })

with open('app/src/main/assets/quran_offline.js', 'w', encoding='utf-8') as f:
    f.write('window.OFFLINE_QURAN=' + json.dumps({'surahs': merged}, ensure_ascii=False, separators=(',', ':')) + ';')
print("Saved quran_offline.js successfully.")

# Bundle Sheikh Abdulrahman Al-Sudais (64kbps) offline audio for Al-Fatiha (1) + Juz Amma (78..114)
target_surahs = [1] + list(range(78, 115))
tasks = []
for s in merged:
    if s['number'] in target_surahs:
        for a in s['ayahs']:
            s_pad = f"{s['number']:03d}"
            a_pad = f"{a['numberInSurah']:03d}"
            tasks.append((s_pad, a_pad))

def download_mp3(item):
    s_pad, a_pad = item
    out_path = f"app/src/main/assets/audio/sudais/{s_pad}{a_pad}.mp3"
    if os.path.exists(out_path) and os.path.getsize(out_path) > 1000:
        return True
    url = f"https://everyayah.com/data/Abdurrahmaan_As-Sudais_64kbps/{s_pad}{a_pad}.mp3"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'MushafAppBuilder/3.0'})
        with urllib.request.urlopen(req, timeout=20) as resp:
            data = resp.read()
        with open(out_path, 'wb') as f:
            f.write(data)
        return True
    except Exception:
        return False

print(f"Bundling {len(tasks)} offline audio ayahs by Sheikh Al-Sudais (Al-Fatiha + Juz Amma)...")
with ThreadPoolExecutor(max_workers=12) as ex:
    results = list(ex.map(download_mp3, tasks))
print(f"Downloaded {sum(results)}/{len(tasks)} offline audio files.")
