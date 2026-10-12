# Conversation presets

Starter vocabulary for the conversation library (original word lists and layout; pictograms are
ARASAAC, CC BY-NC-SA 4.0, https://arasaac.org).

- `conversation-starter.json`: the generated preset (cards, fixed grid positions, colours, labels es/en/ru, ARASAAC ids)
- `vocab.mjs`: the source word lists (edit this to change the vocabulary)
- `build-preset.mjs`: resolves ARASAAC pictograms and writes the JSON and `preview.html` (`node build-preset.mjs <outdir>`; copy `vocab.mjs` next to it)
- `preview.html`: visual review sheet
