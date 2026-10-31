from pathlib import Path
import html
import json
import math
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/demo/.python'))
from docx import Document
from docx.shared import Cm, Pt
from PIL import Image, ImageDraw, ImageFont

OUT = ROOT / 'docs/lab12'
DIAGRAMS = OUT / 'diagrams'
DIAGRAMS.mkdir(parents=True, exist_ok=True)
STATE = json.loads((OUT / 'results/baseline-state.json').read_text(encoding='utf-8'))
SUMMARY = json.loads((OUT / 'results/summary.json').read_text(encoding='utf-8'))


def load_suites(stage):
    return [ET.parse(p).getroot() for p in sorted((OUT / 'results' / stage).glob('TEST-*.xml'))]


class Diagram:
    def __init__(self, title, height=1000):
        self.image = Image.new('RGB', (1500, height), 'white')
        self.draw = ImageDraw.Draw(self.image)
        self.font = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 23)
        self.small = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 21)
        self.bold = ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf', 26)
        self.draw.text((45, 25), title, font=self.bold, fill='#18366b')

    def box(self, x, y, w, title, lines=(), stereotype=None):
        header = 78 if stereotype else 51
        h = header + 18 + 32 * len(lines)
        self.draw.rectangle((x, y, x + w, y + h), fill='#f4f7fc', outline='#274b7a', width=3)
        if stereotype:
            self.draw.text((x + w / 2, y + 15), stereotype, font=self.small, anchor='mt', fill='#274b7a')
        self.draw.text((x + w / 2, y + header - 37), title, font=self.bold, anchor='mt', fill='#142d50')
        self.draw.line((x, y + header, x + w, y + header), fill='#274b7a', width=2)
        for index, line in enumerate(lines):
            self.draw.text((x + 15, y + header + 10 + 32 * index), line, font=self.font, fill='#162b45')
        return (x, y, w, h)

    def arrow(self, start, end, label='', realization=False, composition=False):
        x1, y1 = start
        x2, y2 = end
        length = math.hypot(x2 - x1, y2 - y1)
        ux, uy = (x2 - x1) / length, (y2 - y1) / length
        stop = length - 17
        if realization:
            for distance in range(0, int(stop), 15):
                last = min(distance + 9, stop)
                self.draw.line((x1 + ux * distance, y1 + uy * distance, x1 + ux * last, y1 + uy * last), fill='#496786', width=3)
        else:
            self.draw.line((x1, y1, x2, y2), fill='#496786', width=3)
        base = (x2 - 19 * ux, y2 - 19 * uy)
        triangle = [(x2, y2), (base[0] + 9 * uy, base[1] - 9 * ux), (base[0] - 9 * uy, base[1] + 9 * ux)]
        self.draw.polygon(triangle, fill='white' if realization else '#496786', outline='#496786', width=2)
        if composition:
            self.draw.polygon([(x1, y1), (x1 + 12 * ux + 7 * uy, y1 + 12 * uy - 7 * ux),
                               (x1 + 24 * ux, y1 + 24 * uy), (x1 + 12 * ux - 7 * uy, y1 + 12 * uy + 7 * ux)], fill='#496786')
        if label:
            self.draw.text(((x1 + x2) / 2 + 12, (y1 + y2) / 2 - 28), label, font=self.small, fill='#18366b')

    def save(self, name):
        self.image.save(DIAGRAMS / (name + '.png'))


def diagrams():
    d = Diagram('Composite: before refactoring', 520)
    d.box(60, 150, 800, 'MainActivity / EducationalCatalogScreen', [
        'search + course + semester + type', 'subject + favorites + teacher ownership',
        'inline conjunction of all conditions'], '<<UI component>>')
    d.box(1090, 200, 340, 'CatalogItem', ['material fields'])
    d.arrow((860, 260), (1090, 260), 'filters')
    d.save('composite-before')

    d = Diagram('Composite: after refactoring', 1060)
    d.box(45, 110, 500, 'EducationalCatalogScreen', ['buildCatalogFilter(...)', 'materials.filter(filter::matches)'])
    d.box(900, 110, 500, 'CatalogFilter', ['+ matches(item): Boolean'], '<<interface>>')
    d.arrow((545, 185), (900, 185), 'uses')
    d.box(80, 430, 420, 'AllOfFilter', ['- children: List<CatalogFilter>', '+ matches(item): Boolean'])
    d.box(560, 430, 420, 'AnyOfFilter', ['- children: List<CatalogFilter>', '+ matches(item): Boolean'])
    d.arrow((460, 430), (1080, 245), realization=True)
    d.arrow((940, 430), (1210, 245), realization=True)
    d.arrow((500, 505), (900, 230), 'children 0..*', composition=True)
    d.arrow((980, 505), (1340, 245), 'children 0..*', composition=True)
    d.box(1000, 710, 430, 'Leaf filters', [
        'SearchFilter / CourseFilter', 'SemesterFilter / TypeFilter',
        'SubjectFilter / FavoriteFilter', 'TeacherOwnershipFilter'], '<<group of 7 classes>>')
    d.arrow((1400, 710), (1400, 245), realization=True)
    d.draw.text((55, 950), 'Solid diamond: composite owns child filters. Dashed triangle: interface realization.', font=d.small, fill='#18366b')
    d.save('composite-after')

    d = Diagram('Facade: before refactoring', 660)
    d.box(50, 180, 660, 'TeacherDifficultyPanel', [
        'rank, select attention / positive', 'aggregate subjects, choose top indicators', 'render UI and manage subscriptions'], '<<UI component>>')
    d.box(970, 120, 470, 'DifficultyFeedback.kt', ['rankMaterialDifficulty(...)', 'subjectDifficulty(...)'])
    d.box(970, 400, 470, 'ReactionSummary', ['needsExplanation', 'isWellReceived / difficultyRatio'])
    d.arrow((710, 245), (970, 190), 'coordinates')
    d.arrow((710, 360), (970, 450), 'evaluates')
    d.save('facade-before')

    d = Diagram('Facade: after refactoring', 890)
    d.box(45, 140, 510, 'TeacherDifficultyPanel', ['subscriptions, selected tab, errors', 'display DifficultyAnalysis'], '<<UI component>>')
    d.box(900, 140, 540, 'DifficultyAnalysisFacade', ['+ analyze(materials, summaries)', '    : DifficultyAnalysis'], '<<facade>>')
    d.arrow((555, 240), (900, 240), 'analyze')
    d.box(70, 540, 560, 'DifficultyAnalysis', ['ranked / attention / positive materials', 'subject summaries / top subject', 'best received material'])
    d.box(900, 530, 540, 'Existing analytical subsystem', ['rankMaterialDifficulty(...)', 'subjectDifficulty(...)', 'ReactionSummary properties'])
    d.arrow((1170, 340), (1170, 530), 'coordinates')
    d.arrow((900, 320), (630, 540), 'creates')
    d.arrow((300, 355), (300, 540), 'reads')
    d.save('facade-after')


MERMAID = {
    'composite-before': '''classDiagram
class EducationalCatalogScreen {
  searchAndFilterInline()
  renderCatalog()
}
class CatalogItem
EducationalCatalogScreen --> CatalogItem : filters
''',
    'composite-after': '''classDiagram
class CatalogFilter {
  <<interface>>
  +matches(CatalogItem) Boolean
}
class AllOfFilter
class AnyOfFilter
class SearchFilter
class CourseFilter
class SemesterFilter
class TypeFilter
class SubjectFilter
class FavoriteFilter
class TeacherOwnershipFilter
CatalogFilter <|.. AllOfFilter
CatalogFilter <|.. AnyOfFilter
CatalogFilter <|.. SearchFilter
CatalogFilter <|.. CourseFilter
CatalogFilter <|.. SemesterFilter
CatalogFilter <|.. TypeFilter
CatalogFilter <|.. SubjectFilter
CatalogFilter <|.. FavoriteFilter
CatalogFilter <|.. TeacherOwnershipFilter
AllOfFilter *-- "0..*" CatalogFilter : children
AnyOfFilter *-- "0..*" CatalogFilter : children
EducationalCatalogScreen --> CatalogFilter : matches
CatalogFilterBuilder --> AllOfFilter : creates root
CatalogFilterBuilder --> AnyOfFilter : creates selections
''',
    'facade-before': '''classDiagram
TeacherDifficultyPanel --> DifficultyFeedback : rank and aggregate
TeacherDifficultyPanel --> ReactionSummary : select and compare
class TeacherDifficultyPanel {
  prepareAnalytics()
  renderPanel()
}
''',
    'facade-after': '''classDiagram
TeacherDifficultyPanel --> DifficultyAnalysisFacade : analyze
DifficultyAnalysisFacade --> DifficultyFeedback : rank and aggregate
DifficultyAnalysisFacade --> ReactionSummary : select and compare
DifficultyAnalysisFacade --> DifficultyAnalysis : creates
TeacherDifficultyPanel --> DifficultyAnalysis : displays
DifficultyAnalysis --> SubjectDifficulty
class DifficultyAnalysisFacade {
  +analyze(materials, summaries) DifficultyAnalysis
}
class DifficultyAnalysis {
  rankedMaterials
  attentionMaterials
  positiveMaterials
  subjectSummaries
  mostDifficultSubject
  bestReceivedMaterial
}
'''
}


class Report:
    def __init__(self):
        self.doc = Document()
        self.md = []
        self.htm = []
        section = self.doc.sections[0]
        section.top_margin = section.bottom_margin = Cm(1.8)
        section.left_margin = Cm(2.2)
        section.right_margin = Cm(1.5)
        style = self.doc.styles['Normal']
        style.font.name = 'Times New Roman'
        style.font.size = Pt(12)

    def heading(self, text, level=1):
        self.doc.add_heading(text, level)
        self.md.append('#' * level + ' ' + text + '\n')
        self.htm.append(f'<h{level}>{html.escape(text)}</h{level}>')

    def p(self, text):
        self.doc.add_paragraph(text)
        self.md.append(text + '\n')
        self.htm.append('<p>' + html.escape(text) + '</p>')

    def code(self, text, language='kotlin'):
        paragraph = self.doc.add_paragraph()
        run = paragraph.add_run(text)
        run.font.name = 'Consolas'
        run.font.size = Pt(9)
        self.md.append(f'```{language}\n{text}\n```\n')
        self.htm.append('<pre>' + html.escape(text) + '</pre>')

    def image(self, name, caption):
        path = DIAGRAMS / (name + '.png')
        self.doc.add_picture(str(path), width=Cm(16))
        self.doc.add_paragraph(caption)
        self.md.append(f'![{caption}](diagrams/{name}.png)\n')
        self.htm.append(f'<figure><img src="diagrams/{name}.png"><figcaption>{html.escape(caption)}</figcaption></figure>')

    def table(self, headers, rows):
        table = self.doc.add_table(rows=1, cols=len(headers))
        table.style = 'Table Grid'
        for cell, value in zip(table.rows[0].cells, headers):
            cell.text = str(value)
        for row in rows:
            for cell, value in zip(table.add_row().cells, row):
                cell.text = str(value)
        self.md.extend(['| ' + ' | '.join(headers) + ' |', '|' + '---|' * len(headers)])
        for row in rows:
            self.md.append('| ' + ' | '.join(str(v).replace('|', '/') for v in row) + ' |')
        self.md.append('')
        self.htm.append('<table><tr>' + ''.join('<th>' + html.escape(v) + '</th>' for v in headers) + '</tr>' + ''.join('<tr>' + ''.join('<td>' + html.escape(str(v)) + '</td>' for v in row) + '</tr>' for row in rows) + '</table>')

    def save(self):
        self.doc.save(OUT / 'Лабораторна_12_Звіт.docx')
        (OUT / 'REPORT.md').write_text('\n'.join(self.md), encoding='utf-8')
        css = 'body{font:16px/1.55 system-ui;max-width:1100px;margin:40px auto;padding:0 24px;color:#172c46}h1,h2,h3{color:#234c85}table{border-collapse:collapse;width:100%;margin:20px 0}td,th{border:1px solid #aebed1;padding:8px;text-align:left;vertical-align:top}th{background:#eef3fa}img{max-width:100%}figure{break-inside:avoid}pre{background:#f0f4fa;padding:16px;white-space:pre-wrap;font:13px/1.5 Consolas,monospace}@media print{body{font-size:11pt;margin:0}h2{break-after:avoid}table{font-size:9pt}}'
        (OUT / 'REPORT.html').write_text('<!doctype html><html lang="uk"><meta charset="utf-8"><title>Лабораторна робота 12</title><style>' + css + '</style>' + ''.join(self.htm) + '</html>', encoding='utf-8')


def main():
    diagrams()
    for name, source in MERMAID.items():
        (DIAGRAMS / (name + '.mmd')).write_text(source, encoding='utf-8')
    baseline = load_suites('baseline')
    final = load_suites('final')
    if not baseline or not final:
        raise RuntimeError('JUnit evidence is missing')
    total = lambda suites, key: sum(int(s.attrib[key]) for s in suites)
    r = Report()
    r.heading('Лабораторна робота №12')
    r.p('Тема: вдосконалення архітектури StefanykHub шляхом рефакторингу та впровадження патернів Composite і Facade.')
    r.p('Мета: відокремити правила фільтрації каталогу та координацію аналізу складності від компонентів інтерфейсу, зберігши поточну поведінку застосунку й перевіривши результат автоматизованими тестами.')
    r.p('Дата виконання: 31.10.2026. Проєкт: Android-застосунок StefanykHub, Kotlin, Jetpack Compose, Firebase. Робота виконана за умовою Lab 12.docx.')
    r.heading('1. Аналіз і фіксація початкового стану', 2)
    r.p('Застосунок містить каталог навчальних матеріалів із пошуком, фільтрами, обраним і пагінацією. Панель викладача показує реакції студентів, рейтинг складності й зведення за дисциплінами. Доступ до Firebase та реактивний стан зосереджені в AppRepository; моделі й чисті функції обчислень розташовані в model; інтерфейс побудований на Compose.')
    r.p(f'Створено окрему гілку {STATE["branch"]}. Початковий коміт: {STATE["baseCommit"]}. Початкові версії двох змінених UI-компонентів збережено в results/*.before.txt. Наявний локальний .idea/vcs.xml не змінювався.')
    r.p(f'До рефакторингу виконано {total(baseline, "tests")} модульні тести: помилок {total(baseline, "failures") + total(baseline, "errors")}. Протоколи JUnit збережено в results/baseline/. Базові сценарії обчислення реакцій, складності та навчальної статистики пройшли.')
    r.p('Запуск застосунку й ручні UI-сценарії до та після змін не перевірялися: adb devices не виявив підключеного пристрою або емулятора. Результат збірки APK та JVM-тести не прирівнюються до успішного запуску на Android.')
    r.heading('2. Composite: композиція фільтрів каталогу', 2)
    r.heading('2.1. Початкова проблема й обґрунтування', 3)
    r.p('У EducationalCatalogScreen файлу MainActivity.kt один блок materials.filter одночасно перевіряв пошук, курс, семестр, тип, дисципліну, обране та належність викладачу. Додавання умови вимагало редагувати UI-функцію; правила не мали незалежного інтерфейсу для повторного використання або вкладених комбінацій.')
    r.p('Composite обрано тому, що окрема умова й група умов виконують однакову операцію: визначають, чи підходить матеріал. Спільний CatalogFilter дозволяє вкладати AllOfFilter та AnyOfFilter один в одного. Це дерево об’єктів із єдиним інтерфейсом, а не лише винесена функція з тим самим набором if.')
    r.image('composite-before', 'Рисунок 1 — залежності фільтрації до рефакторингу.')
    r.heading('2.2. Нова структура та інтеграція', 3)
    r.table(['Учасник', 'Роль'], [
        ['CatalogFilter', 'Component: matches(CatalogItem): Boolean.'],
        ['SearchFilter, CourseFilter, SemesterFilter, TypeFilter, SubjectFilter, FavoriteFilter, TeacherOwnershipFilter', 'Leaf: окрема умова відбору.'],
        ['AllOfFilter', 'Composite: усі дочірні умови повинні виконуватися (AND).'],
        ['AnyOfFilter', 'Composite: достатньо однієї дочірньої умови (OR).'],
        ['buildCatalogFilter()', 'Збирає дерево з поточних налаштувань; це допоміжна функція, не окреме впровадження патерна Builder.'],
        ['EducationalCatalogScreen', 'Client: будує фільтр і застосовує його до матеріалів.']
    ])
    r.image('composite-after', 'Рисунок 2 — UML після впровадження Composite. Сім листових класів згруповано для читабельності; повна структура наведена в composite-after.mmd.')
    r.code('fun interface CatalogFilter {\n    fun matches(item: CatalogItem): Boolean\n}\n\nclass AllOfFilter(private val children: List<CatalogFilter>) : CatalogFilter {\n    override fun matches(item: CatalogItem) = children.all { it.matches(item) }\n}\n\nclass AnyOfFilter(private val children: List<CatalogFilter>) : CatalogFilter {\n    override fun matches(item: CatalogItem) = children.any { it.matches(item) }\n}')
    r.p('Корінь дерева — AllOfFilter. Усередині нього SearchFilter та три AnyOfFilter для вибраних курсів, семестрів і типів. SubjectFilter, FavoriteFilter та TeacherOwnershipFilter додаються лише коли потрібні. Приклад реального відбору: пошук SQL AND (2 курс OR 3 курс) AND (3 семестр OR 4 семестр) AND Лабораторні AND Обране.')
    r.code('val catalogFilter = buildCatalogFilter(\n    searchQuery = query,\n    selectedCourses = selectedCourses,\n    selectedSemesters = selectedSemesters,\n    selectedTypes = selectedTypes,\n    selectedSubject = selectedSubject,\n    favoritesOnly = isFavoriteFilterActive,\n    teacherId = if (isTeacher) user.id else null\n)\nval filteredItems = materials.filter(catalogFilter::matches)')
    r.p('Збережено нечутливий до регістру пошук у шести полях та обрізання пробілів запиту. Залишено зіставлення Лекція → Лекції та Лабораторна → Лабораторні. Порожній AnyOfFilter відхиляє всі матеріали, тому зняття всіх значень категорії дає нуль результатів. Порожній AllOfFilter приймає всі матеріали. Вимкнені необов’язкові обмеження не додаються до дерева. Порядок матеріалів і пагінація збережені; серверні правила доступу не змінювалися.')
    r.heading('2.3. Переваги та недоліки', 3)
    r.p('Переваги: UI не реалізує самі предикати; фільтри можна тестувати й повторно комбінувати; лист і група взаємозамінні; складні вкладені умови не потребують змін у механізмі обходу дерева. Недоліки: більше невеликих класів і об’єктів; дерево складніше за короткий одноразовий предикат; для нової категорії треба також оновити побудову дерева та стан інтерфейсу. Патерн не гарантує прискорення фільтрації.')
    r.heading('3. Facade: єдина точка входу до аналізу складності', 2)
    r.heading('3.1. Початкова проблема й обґрунтування', 3)
    r.p('TeacherDifficultyPanel безпосередньо викликав rankMaterialDifficulty та subjectDifficulty, відбирав матеріали з needsExplanation та isWellReceived, знаходив дисципліну з найбільшим difficultyRatio й матеріал із найбільшою кількістю позитивних реакцій. UI знав послідовність підготовки аналітики. Інший клієнт мав би повторити цю координацію.')
    r.image('facade-before', 'Рисунок 3 — панель координує аналітичну підсистему до змін.')
    r.p('Facade надає один метод analyze(materials, summaries), за яким прихована координація наявних операцій. Чисті функції підсистеми залишаються доступними; фасад спрощує типовий сценарій їх спільного використання. Створювати штучні класи для кожного обчислення не потрібно.')
    r.heading('3.2. Нова структура та інтеграція', 3)
    r.table(['Компонент', 'Відповідальність'], [
        ['DifficultyAnalysisFacade', 'Будує рейтинг, списки уваги й позитивних реакцій, підсумки дисциплін та обирає підсумкові показники.'],
        ['DifficultyAnalysis', 'Містить rankedMaterials, attentionMaterials, positiveMaterials, subjectSummaries, mostDifficultSubject, bestReceivedMaterial.'],
        ['SubjectDifficulty', 'Типізовано поєднує назву дисципліни з ReactionSummary.'],
        ['rankMaterialDifficulty / subjectDifficulty / ReactionSummary', 'Наявна підсистема обчислень; алгоритми й пороги залишені без змін.'],
        ['TeacherDifficultyPanel', 'Передає актуальний знімок реакцій, відображає результат, керує підписками, помилками, вкладками й діалогом.']
    ])
    r.image('facade-after', 'Рисунок 4 — панель отримує підготовлений результат через фасад.')
    r.code('val facade = remember { DifficultyAnalysisFacade() }\nval analysis = facade.analyze(owned, AppRepository.materialReactions.toMap())\nval reports = analysis.rankedMaterials\nval alerts = analysis.attentionMaterials\nval positive = analysis.positiveMaterials')
    r.p('Фасад не залежить від Android, Compose або Firebase. Він аналізує лише передану вибірку матеріалів. Відбір власних матеріалів викладача та керування підписками залишені у панелі. remember зберігає лише екземпляр фасаду; результат перераховується з актуального стану, тому нові реакції не приховуються кешованою аналітикою.')
    r.p('Збережено пороги: сигнал уваги — щонайменше 3 проблемні реакції та їх частка понад 50%; позитивний сигнал — щонайменше 3 позитивні реакції та їх частка понад 50%. Рейтинг спочатку враховує потребу в поясненні, потім частку складності, кількість проблемних реакцій, загальну кількість реакцій та id. Підсумок дисципліни обчислюється сумуванням голосів, а не середнім відсотків матеріалів.')
    r.p('Відсутній запис у summaries означає, що дані ще не отримані: такий матеріал не потрапляє до рейтингу. Наявний порожній ReactionSummary є завантаженим результатом без голосів і залишається у рейтингу. Для порожніх підсумків mostDifficultSubject та bestReceivedMaterial дорівнюють null. Попередні правила вибору при рівності показників збережено.')
    r.heading('3.3. Переваги та недоліки', 3)
    r.p('Переваги: одна точка входу для клієнтів аналітики; менше координації у Compose; можливість повторного використання підготовки результатів та звичайного JVM-тестування. Недоліки: додано модель результату та шар виклику; фасад залежить від складу підсумкової аналітики. Його слід обмежувати аналізом складності, не перетворюючи на універсальний сервіс. У цій невеликій підсистемі головна користь — відокремлення відповідальностей, а не скорочення кількості операцій.')
    r.heading('4. Змінені та додані файли', 2)
    r.table(['Файл відносно app/src/', 'Зміна'], [
        ['main/java/com/example/stefanykhub/model/catalog/CatalogFilter.kt', 'Інтерфейс та дві рекурсивні композиції.'],
        ['main/java/com/example/stefanykhub/model/catalog/CatalogFilters.kt', 'Сім листових фільтрів.'],
        ['main/java/com/example/stefanykhub/model/catalog/CatalogFilterBuilder.kt', 'Побудова дерева для поточного каталогу.'],
        ['main/java/com/example/stefanykhub/MainActivity.kt', 'Заміна inline-перевірок на CatalogFilter.'],
        ['main/java/com/example/stefanykhub/model/DifficultyAnalysis.kt', 'Моделі результату аналітики.'],
        ['main/java/com/example/stefanykhub/model/DifficultyAnalysisFacade.kt', 'Координація підготовки аналітики.'],
        ['main/java/com/example/stefanykhub/ui/components/TeacherDifficultyPanel.kt', 'Використання фасаду.'],
        ['test/java/com/example/stefanykhub/CatalogFilterTest.kt', '10 нових тестів.'],
        ['test/java/com/example/stefanykhub/DifficultyAnalysisFacadeTest.kt', '8 нових тестів.']
    ])
    r.p('DifficultyFeedbackTest.kt не змінено: 9 його перевірок залишаються регресійною основою алгоритмів складності. Нові файли Kotlin написані без коментарів. Залежності проєкту й AppRepository для цих патернів не змінювалися.')
    r.heading('5. Результати тестування', 2)
    r.table(['Набір', 'До змін', 'Після змін', 'Помилки після'], [
        [suite.attrib['name'].split('.')[-1], next((s.attrib['tests'] for s in baseline if s.attrib['name'] == suite.attrib['name']), '—'),
         suite.attrib['tests'], int(suite.attrib['failures']) + int(suite.attrib['errors'])] for suite in final
    ] + [['Разом', total(baseline, 'tests'), total(final, 'tests'), total(final, 'failures') + total(final, 'errors')]])
    r.p('CatalogFilterTest перевіряє AND, OR, вкладені композиції, порожні групи, пошук у шести полях, назви типів, поєднання обмежень, порожні вибірки й вимкнені необов’язкові умови. Окремий регресійний тест порівнює результати зі старим предикатом на 2592 конфігураціях та п’яти матеріалах. Це один JUnit-тест із 2592 наборами налаштувань, а не 2592 окремі тести. Кінцева вибірка не доводить еквівалентність для всіх можливих даних.')
    r.p('DifficultyAnalysisFacadeTest перевіряє порожній результат; рейтинг і поділ матеріалів; зважену складність дисциплін; різницю між відсутніми та порожніми реакціями; ігнорування реакцій поза вибіркою; вибір позитивного матеріалу; повторний аналіз нового стану; збереження порядку за рівних показників.')
    r.p(f'Фінальний результат: {total(final, "tests")} тести, помилок {total(final, "failures") + total(final, "errors")}, пропущено {total(final, "skipped")}. Команда :app:assembleDebug завершилася BUILD SUCCESSFUL. APK: {SUMMARY["apk"]["path"]}; розмір {SUMMARY["apk"]["bytes"]} байтів. Протоколи: results/final/TEST-*.xml; загальний підсумок і SHA-256 APK: results/summary.json.')
    r.p('Результати підтверджують перевірені правила фільтрації й аналітики та успішну компіляцію інтегрованих UI-компонентів. Інструментальні тести, ручна перевірка інтерфейсу та перевірка запуску на пристрої в межах цієї роботи не виконані.')
    r.heading('6. Порівняння до та після', 2)
    r.table(['Аспект', 'До', 'Після'], [
        ['Фільтрація', 'Набір перевірок усередині UI.', 'Дерево взаємозамінних фільтрів AND/OR.'],
        ['Аналітика', 'Панель координує кілька операцій.', 'Один виклик фасаду повертає результат.'],
        ['Тестування', 'Базові обчислення реакцій і статистики.', 'Додатково композиція умов і повний результат аналізу.'],
        ['Правила предметної області', 'Пошук, відбір, пороги й зважені показники.', 'Збережені й перевірені регресійно.'],
        ['Складність структури', 'Менше класів; більше відповідальностей UI.', 'Більше класів; чіткіший розподіл відповідальностей.']
    ])
    r.heading('7. Відтворення перевірок', 2)
    r.code(SUMMARY['finalCommand'] + '\npython tools/demo/build-lab12-report.py', 'powershell')
    r.p('Для Gradle потрібні встановлені JDK і Android SDK. Скрипт звіту використовує python-docx і Pillow; у цьому середовищі вони доступні в tools/demo/.python. Звіт відтворюється зі збережених протоколів лабораторної, а не підміняє їх новими статусами. Для фіксації нового запуску потрібно окремо оновити results/final/ і summary.json.')
    r.heading('Висновки', 2)
    r.p('У StefanykHub впроваджено два структурні патерни на основі реальних передумов. Composite переніс правила каталогу з UI у дерево фільтрів зі спільним інтерфейсом. Facade відокремив координацію аналізу складності від панелі викладача, зберігши наявні функції та правила обчислення. Патерни інтегровані в робочі виклики екранів, а не додані як невикористані демонстраційні класи.')
    r.p(f'Після рефакторингу пройшли всі {total(final, "tests")} модульні тести та debug-збірка. Порівняння фільтрації на 2592 конфігураціях не виявило відмінностей від початкової реалізації. Отримано можливість незалежно тестувати й повторно використовувати правила відбору та підготовку аналітики. Додаткові абстракції збільшили кількість класів, але зменшили відповідальність UI-компонентів. Перевірка запуску й UI на Android залишається окремим невиконаним етапом.')
    r.save()
    print(f'Generated DOCX, Markdown, HTML and four UML diagrams; {total(final, "tests")} tests in saved evidence.')


if __name__ == '__main__':
    main()
