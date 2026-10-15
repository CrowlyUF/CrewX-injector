# Menu assets

`render_menu_assets.py` renders everything in `src/main/resources/assets/crewx/menu/`
(space background layers, clock digit atlas and menu icons). It only needs Python 3 with numpy and Pillow:

    python3 render_menu_assets.py <nasa-texture-dir> <InterDisplay-Bold.otf> ../../src/main/resources/assets/crewx/menu

- Earth surface, clouds and night lights: NASA Visible Earth / Black Marble (public domain), URLs in the script.
- Clock digits: Inter Display Bold, SIL Open Font License 1.1 (only 0-9 and ':' are rasterised).
