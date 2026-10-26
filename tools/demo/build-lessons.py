"""Build the bundled reading PDFs. Run with reportlab installed."""
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).parent / '.python'))
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Paragraph, Table, TableStyle
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.colors import HexColor, white
from xml.sax.saxutils import escape

ROOT = Path(__file__).parent / 'materials'
ROOT.mkdir(exist_ok=True)
for name, file in [('Body', 'arial.ttf'), ('Bold', 'arialbd.ttf'), ('Code', 'consola.ttf')]:
    pdfmetrics.registerFont(TTFont(name, 'C:/Windows/Fonts/' + file))
INK, MUTED, BLUE = map(HexColor, ['#17243B', '#52627A', '#355CDB'])

class Lesson:
    def __init__(self, filename, subject):
        self.c = canvas.Canvas(str(ROOT / filename), pagesize=(420, 595))
        self.c.setTitle(subject)
        self.c.setAuthor('Stefanyk.Hub')
        self.subject, self.page = subject, 0

    def start(self, title, subtitle):
        if self.page: self.c.showPage()
        self.page += 1
        c = self.c
        c.setFillColor(BLUE); c.rect(0, 582, 420, 13, fill=1, stroke=0)
        c.setFont('Bold', 10); c.drawString(28, 555, 'STEFANYK.HUB / НАВЧАЛЬНІ МАТЕРІАЛИ')
        c.setFillColor(MUTED); c.setFont('Body', 9)
        c.drawString(28, 22, self.subject)
        c.drawRightString(392, 22, f'{self.page} / 4')
        self.y = 530
        self.p(title, size=24, font='Bold', color=INK, gap=9)
        self.p(subtitle, size=11, color=MUTED, gap=18)

    def p(self, text, size=13, font='Body', color=INK, gap=9):
        style = ParagraphStyle('p', fontName=font, fontSize=size, leading=size*1.28, textColor=color)
        p = Paragraph(escape(text).replace('\n', '<br/>'), style)
        _, height = p.wrap(364, 600)
        assert self.y-height > 42, f'Page overflow: {text}'
        p.drawOn(self.c, 28, self.y-height)
        self.y -= height+gap

    def h(self, text): self.p(text, size=16, font='Bold', color=BLUE, gap=8)

    def code(self, lines):
        height = 22 + len(lines)*17
        assert self.y-height > 42
        self.c.setFillColor(HexColor('#EEF2FA'))
        self.c.roundRect(28, self.y-height, 364, height, 7, fill=1, stroke=0)
        self.c.setFillColor(INK); self.c.setFont('Code', 11)
        for i,line in enumerate(lines):
            assert pdfmetrics.stringWidth(line,'Code',11) < 342
            self.c.drawString(39, self.y-20-i*17, line)
        self.y -= height+14

    def table(self, rows):
        style = ParagraphStyle('cell', fontName='Body', fontSize=12, leading=17, textColor=INK)
        cells = [[Paragraph(escape(cell),style) for cell in row] for row in rows]
        table = Table(cells,colWidths=[152,212])
        table.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),HexColor('#DEE7FF')),('ROWBACKGROUNDS',(0,1),(-1,-1),[white,HexColor('#F2F5FA')]),('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),10),('RIGHTPADDING',(0,0),(-1,-1),10),('TOPPADDING',(0,0),(-1,-1),8),('BOTTOMPADDING',(0,0),(-1,-1),8)]))
        _,height=table.wrap(364,600)
        assert self.y-height>42
        table.drawOn(self.c,28,self.y-height); self.y-=height+16

    def save(self): self.c.save()

d=Lesson('databases.pdf','Бази даних • SQL')
d.start('Бази даних: від таблиці до SQL','Практичний конспект • 25–35 хвилин • початковий рівень')
d.h('01 / Як організовані дані')
d.p('База даних зберігає інформацію в таблицях. Рядок — один запис, стовпець — його властивість. У таблиці students кожен рядок описує студента.')
d.table([['id','name'],['1','Olena'],['2','Taras'],['3','Iryna']])
d.p('Первинний ключ (PRIMARY KEY) однозначно визначає запис. Ім’я може повторюватися, тому для ідентифікації використовуємо id.')
d.h('Що навчишся робити')
d.p('Створювати таблиці, додавати записи, фільтрувати дані та поєднувати їх через JOIN.',size=12)
d.start('Створюємо й читаємо','02 / Виконай приклади послідовно в SQLite')
d.h('Структура та перші записи')
d.code(['CREATE TABLE students (','  id INTEGER PRIMARY KEY,','  name TEXT NOT NULL',');',"INSERT INTO students VALUES", "  (1, 'Olena'), (2, 'Taras');"])
d.p('INTEGER — ціле число, TEXT — текст. Обмеження NOT NULL забороняє порожнє значення NULL у полі name.',size=12)
d.h('Вибірка та сортування')
d.code(['SELECT id, name','FROM students','WHERE id > 1','ORDER BY name;'])
d.p('SELECT обирає стовпці, WHERE залишає потрібні рядки, ORDER BY сортує. Результат цього запиту — рядок 2, Taras.',size=12)
d.start('Зв’язки між таблицями','03 / Один студент може записатися на кілька курсів')
d.p('Зовнішній ключ (FOREIGN KEY) посилається на запис іншої таблиці. Таблиця enrollments зберігає, хто на який курс записався.')
d.code(['CREATE TABLE enrollments (','  student_id INTEGER,','  course TEXT NOT NULL,','  FOREIGN KEY (student_id)', '    REFERENCES students(id)',');',"INSERT INTO enrollments VALUES", "  (1, 'English'), (2, 'Databases');"])
d.h('JOIN: отримуємо імена та курси')
d.code(['SELECT s.name, e.course','FROM students AS s','JOIN enrollments AS e','  ON s.id = e.student_id;'])
d.p('Результат: Olena — English; Taras — Databases. У SQLite для перевірки зовнішніх ключів увімкни PRAGMA foreign_keys = ON перед записом даних.',size=11)
d.start('Практика та самоперевірка','04 / Спробуй самостійно, потім звірся')
d.h('Завдання')
d.p('1. Створи таблицю books з полями id, title і year. Додай три книги з різними роками видання.')
d.p('2. Виведи лише назви книг, виданих після 2020 року. Відсортуй їх за назвою.')
d.h('Один із можливих запитів')
d.code(['SELECT title FROM books','WHERE year > 2020','ORDER BY title;'])
d.h('Пам’ятай про надійність')
d.p('Транзакція об’єднує зміни: COMMIT зберігає їх, ROLLBACK скасовує. Для введених користувачем значень використовуй параметри запиту замість складання SQL із текстових рядків.',size=12)
d.h('Перевір себе')
d.p('Чим відрізняються первинний і зовнішній ключі? Перший визначає запис у власній таблиці, другий посилається на запис пов’язаної таблиці.',size=12)
d.save()

e=Lesson('english.pdf','Англійська для ІТ • A2–B1')
e.start('English for IT','Team communication • A2–B1 • 20–30 хвилин')
e.h('01 / Слова, які потрібні щодня')
e.p('Мета: коротко розповідати про свою роботу, описувати проблему й ввічливо просити допомоги.')
e.table([['English','Українською'],['task / deadline','завдання / кінцевий термін'],['issue / bug','проблема / помилка в програмі'],['fix / review','виправити / перевірити роботу'],['deploy / update','розгорнути / оновити'],['blocked','не можу продовжити роботу']])
e.h('Фраза в контексті')
e.p('Could you review my changes? — Чи можеш перевірити мої зміни?')
e.p('The deadline is Friday. — Кінцевий термін — п’ятниця.',size=12)
e.start('Your daily stand-up','02 / Yesterday → Today → Blockers')
e.h('Зразок короткого звіту')
e.p('Yesterday I fixed the login screen.\nToday I am testing the database connection.\nI am blocked because the server is unavailable.')
e.p('Учора я виправила екран входу. Сьогодні перевіряю з’єднання з базою даних. Не можу продовжити, тому що сервер недоступний.',size=12,color=MUTED)
e.h('Три часи — три ситуації')
e.table([['Коли?','Приклад'],['Зазвичай: Present Simple','I test the app every day.'],['Зараз: Present Continuous','I am testing the app now.'],['Учора: Past Simple','I tested the app yesterday.']])
e.p('Зверни увагу: fix → fixed, test → tested, але write → wrote. Для дії зараз: am/is/are + дієслово з -ing.',size=12)
e.start('Ask for help clearly','03 / Опиши очікування, результат і кроки')
e.h('Діалог у команді')
e.p('Olena: The PDF does not open on my phone.\nAlex: What happens when you tap Open?\nOlena: I see a network error.\nAlex: Is the local server running?\nOlena: No. I will start it and try again.')
e.h('Корисні конструкції')
e.p('Could you help me reproduce this issue?\nЧи можеш допомогти відтворити цю проблему?',size=12)
e.p('I expected the document to open, but I got an error.\nЯ очікувала, що документ відкриється, але отримала помилку.',size=12)
e.h('Шаблон повідомлення')
e.p('When I [action], I see [result].\nI expected [expected result].\nCould you help me check [component]?',size=12)
e.start('Practice & answer key','04 / Спочатку виконай, потім перевір')
e.h('A. Обери правильну форму')
e.p('1. Yesterday I (fix / fixed) a bug.\n2. Now she (tests / is testing) the app.\n3. We (review / reviewed) code every day.')
e.h('B. Переклади англійською')
e.p('«Чи можеш перевірити мої зміни?»\n«Учора я перевірила з’єднання з базою даних».')
e.h('C. Твій звіт команді')
e.p('Напиши три речення: що зробила вчора, що робиш сьогодні й що заважає. Використай слова task, test або issue.')
e.h('Відповіді для самоперевірки')
e.p('A: 1 — fixed; 2 — is testing; 3 — review.\nB: Could you review my changes?\nYesterday I tested the database connection.',size=12)
e.p('У звіті C перевір: чи є Yesterday / Today, правильний час і зрозумілий опис перешкоди.',size=11,color=MUTED)
e.save()
print('Built databases.pdf and english.pdf (4 pages each).')
