"""Render the lab report from actual JUnit XML, TAP and Firestore observations."""
from pathlib import Path
from datetime import datetime
import sys, json, html, re, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/demo/.python'))
from docx import Document
from docx.shared import Cm, Pt
OUT=ROOT/'docs/lab11'
RESULTS=OUT/'results'
EVIDENCE=OUT/'evidence'
data=json.loads((RESULTS/'lab11-integration.json').read_text(encoding='utf-8'))
xml=ET.parse(RESULTS/'ui-final.xml')
observed={case.attrib['name']:case for case in xml.iter('testcase')}
cases=[
 ('TC001','Правильна авторизація','Позитивний','Зареєстрований студент із підтвердженою поштою; 2FA вимкнена.','student.demo@pnu.edu.ua / DemoPass123!','Відкрити вхід; натиснути «Студент»; натиснути «Увійти».','Відкрито каталог із двома матеріалами та ім’ям Олена Тестова.'),
 ('TC002','Пошук наявного матеріалу','Позитивний','Студент авторизований; у базі два матеріали.','SQL','Відкрити пошук; ввести SQL.','Показано один документ — «Бази даних: таблиці та SQL».'),
 ('TC003','Фільтрація та скидання фільтрів','Позитивний','Студент авторизований; обидва матеріали належать до 2 курсу.','Курс: 2 курс','Відкрити фільтри; вимкнути 2 курс; натиснути системну «Назад»; перевірити 0 результатів; знову відкрити фільтри; натиснути «Скинути» та «Назад».','Панель закривається без виходу із застосунку; після скидання знову видно 2 документи.'),
 ('TC004','Відкриття PDF з англійської','Позитивний','Студент авторизований; Storage доступний; файл англійської не завантажений офлайн.','Англійська','Знайти матеріал за словом «Англійська»; відкрити картку; натиснути «Читати матеріал»; дочекатися PDF.','Ридер відображає «Сторінка 1 / 4» та зображення першої сторінки.'),
 ('TC005','Відображення навчальної статистики','Позитивний','У демо студента опрацьовані 2 матеріали з 2.','2 опрацьовані / 2 доступні','Увійти; переглянути блок «Прогрес навчання».','Відображено «Загальний прогрес» та 100% (2/2).'),
 ('TC006','Вихід із системи','Позитивний','Студент авторизований.','Кнопка виходу','Натиснути «Вийти з акаунту» у заголовку каталогу.','Показано форму входу; каталог більше не відображається.'),
 ('TC007','Неправильний пароль','Негативний','Акаунт студента існує; ліміт спроб не вичерпаний.','student.demo@pnu.edu.ua / WrongPassword42!','Заповнити дані студента; замінити пароль на неправильний; натиснути «Увійти».','Показано повідомлення «Не вдалося підтвердити облікові дані»; каталог не відкривається.'),
 ('TC008','Порожня електронна пошта','Негативний','Відкрита форма входу.','Пошта порожня; пароль DemoPass123!','Залишити пошту порожньою; ввести пароль; натиснути «Увійти».','Показано «Введіть електронну пошту!».'),
 ('TC009','Порожній пароль','Негативний','Відкрита форма входу.','student.demo@pnu.edu.ua; пароль порожній','Ввести пошту; залишити пароль порожнім; натиснути «Увійти».','Показано «Введіть пароль!».'),
 ('TC010','Пошта стороннього домену','Негативний','Відкрита форма входу.','student@gmail.com / DemoPass123!','Ввести пошту Gmail та пароль; натиснути «Увійти».','Показано вимогу використовувати @pnu.edu.ua або @stefanyk.edu.ua.'),
 ('TC011','Слабкий пароль реєстрації','Негативний','Відкрита вкладка «Реєстрація».','Пароль abc; решта полів не заповнена','Ввести abc в поле пароля; натиснути «Зареєструватися»; перевірити помилку пароля.','Показано вимогу 8–128 символів; повідомлення «Акаунт створено» відсутнє. Перевірка стосується виявлення слабкого пароля в незаповненій формі.'),
 ('TC012','Пошук відсутнього матеріалу','Негативний','Студент авторизований.','zzzz-no-such-material-98765','Відкрити пошук; ввести запит, який не відповідає жодному матеріалу.','Показано 0 документів; картки обох матеріалів відсутні.'),
]

class Report:
 def __init__(self):
  self.doc=Document();self.md=[];self.htm=[]
  section=self.doc.sections[0];section.top_margin=Cm(1.8);section.bottom_margin=Cm(1.8);section.left_margin=Cm(2);section.right_margin=Cm(1.5)
  style=self.doc.styles['Normal'];style.font.name='Times New Roman';style.font.size=Pt(12)
 def heading(self,text,level=1):
  self.doc.add_heading(text,level);self.md.append('#'*level+' '+text+'\n');self.htm.append(f'<h{level}>{html.escape(text)}</h{level}>')
 def p(self,text):
  self.doc.add_paragraph(text);self.md.append(text+'\n');self.htm.append('<p>'+html.escape(text)+'</p>')
 def table(self,headers,rows):
  table=self.doc.add_table(rows=1,cols=len(headers));table.style='Table Grid'
  for cell,text in zip(table.rows[0].cells,headers):cell.text=str(text)
  for row in rows:
   for cell,text in zip(table.add_row().cells,row):cell.text=str(text)
  self.md.extend(['| '+' | '.join(headers)+' |','|'+'---|'*len(headers)])
  for row in rows:self.md.append('| '+' | '.join(str(x).replace('\n','<br>').replace('|','/') for x in row)+' |')
  self.md.append('')
  self.htm.append('<table><thead><tr>'+''.join('<th>'+html.escape(x)+'</th>' for x in headers)+'</tr></thead><tbody>'+''.join('<tr>'+''.join('<td>'+html.escape(str(x))+'</td>' for x in row)+'</tr>' for row in rows)+'</tbody></table>')
 def image(self,path,caption,width=8):
  if not path.exists():raise FileNotFoundError(path)
  self.doc.add_picture(str(path),width=Cm(width));self.doc.add_paragraph(caption)
  relative=path.relative_to(OUT).as_posix();self.md.append(f'![{caption}]({relative})\n');self.htm.append(f'<figure><img src="{relative}" alt="{html.escape(caption)}"><figcaption>{html.escape(caption)}</figcaption></figure>')
 def code(self,text):
  self.doc.add_paragraph(text,style='No Spacing');self.md.append('```powershell\n'+text+'\n```\n');self.htm.append('<pre>'+html.escape(text)+'</pre>')
 def save(self):
  self.doc.save(OUT/'Лабораторна_11_Звіт.docx')
  (OUT/'REPORT.md').write_text('\n'.join(self.md),encoding='utf-8')
  (OUT/'REPORT.html').write_text('<!doctype html><html lang="uk"><meta charset="utf-8"><title>Лабораторна робота 11</title><style>body{font:16px/1.55 system-ui;max-width:1100px;margin:40px auto;padding:0 25px;color:#182238}h1,h2,h3{color:#2847a2}table{border-collapse:collapse;width:100%;font-size:13px;margin:20px 0}td,th{border:1px solid #bdc8db;padding:8px;text-align:left;vertical-align:top}th{background:#eaf0ff}figure{break-inside:avoid}img{max-height:760px;max-width:100%;border:1px solid #ddd}pre{background:#edf1f8;padding:16px;white-space:pre-wrap}@media print{body{margin:0;font-size:11pt}h2{break-after:avoid}table{font-size:9pt}img{max-height:21cm}}</style>'+''.join(self.htm)+'</html>',encoding='utf-8')

def status_for(prefix):
 matches=[(name,node) for name,node in observed.items() if name.startswith(prefix+'_')]
 if len(matches)!=1:raise ValueError('Missing UI evidence: '+prefix)
 name,node=matches[0];return name,('FAIL' if node.find('failure') is not None or node.find('error') is not None else 'PASS')

def evidence_pages():
 css='body{font:18px/1.5 system-ui;padding:35px;color:#14233a;max-width:1150px;margin:auto}h1{color:#2847a2}.ok{color:#187447}pre{font:15px/1.5 Consolas,monospace;background:#eef2f8;padding:20px;white-space:pre-wrap}table{border-collapse:collapse;width:100%}td,th{padding:10px;border:1px solid #bdc8db;text-align:left}'
 tap=(RESULTS/'unit-tests.tap').read_text(encoding='utf-8-sig')
 assert '# pass 5' in tap and '# fail 0' in tap
 (EVIDENCE/'unit-report.html').write_text(f'<!doctype html><meta charset="utf-8"><style>{css}</style><h1>Test Execution Report — Unit</h1><p>Stefanyk.Hub · node:test · фактичний вивід запуску</p><p class="ok">5 тестів виконано, 5 PASS, 0 FAIL</p><pre>'+html.escape(tap)+'</pre>',encoding='utf-8')
 registration=data['cases'][0]['details'];activation=data['cases'][1]['details']
 body='<h1>Інтеграція API та Firestore</h1><p>Фактичні дані, прочитані Admin SDK з локальної бази demo-stefanykhub.</p><p>'+html.escape(data['executedAt'])+'</p>'
 body+='<table><tr><th>ID</th><th>Перевірка</th><th>Результат</th></tr>'+''.join('<tr><td>'+case['id']+'</td><td>'+case['title']+'</td><td>'+case['status']+'</td></tr>' for case in data['cases'])+'</table>'
 body+='<h2>IT-001 — студент створений у базі</h2><pre>'+html.escape(json.dumps(registration,ensure_ascii=False,indent=2))+'</pre>'
 body+='<p>IT-002: після активації документ існує в users; читання профілю повернуло HTTP '+str(activation['profileReadStatus'])+'. Тестовий акаунт видалено після перевірки.</p>'
 (EVIDENCE/'firestore-report.html').write_text('<!doctype html><meta charset="utf-8"><style>'+css+'</style>'+body,encoding='utf-8')

def main():
 evidence_pages()
 if '--evidence-only' in sys.argv:return
 r=Report();r.heading('ЛАБОРАТОРНА РОБОТА № 11')
 r.p('Тема: Тестування програмного забезпечення. Програмний продукт: мобільний застосунок «Stefanyk.Hub».')
 r.p('Виконала: ____________________    Група: ____________________')
 r.p('Мета: набути навичок розроблення тест-кейсів, виконання функціонального, модульного та інтеграційного тестування, фіксації результатів і опису дефектів.')
 r.heading('1. Об’єкт і середовище тестування',2)
 r.p('Застосунок призначений для доступу до навчальних матеріалів. Тестування проведено на Android Emulator (Medium Phone, API 37 / Android 17), debug-збірка, локальний проєкт demo-stefanykhub. Використано Kotlin, JUnit4, Compose UI Test, Espresso 3.7.0, Node.js node:test та Firebase Emulator Suite. Мережеві відповіді у UI-тестах не підмінялися.')
 r.p('Адреси: API безпеки — localhost:8787; Authentication — 9099; Firestore — 8080; Storage — 9199. У базі один демо студент і два PDF. Додатковий студент створювався тимчасово для інтеграційних перевірок та видалявся після завершення.')
 r.p('Час інтеграційного запуску (UTC): '+data['executedAt']+'. Дата й час UI-запуску містяться в оригінальному JUnit XML у results/ui-final.xml.')
 r.table(['Функція','Об’єкт тестування','Тест-кейси'],[
 ['Реєстрація студента','Форма реєстрації, API та профіль у БД','TC011; IT-001'],['Авторизація','Форма входу, API','TC001; TC007'],['Перевірка обов’язкових полів та домену','Поля пошти й пароля','TC008–TC011'],['Пошук','Поле пошуку каталогу','TC002; TC012'],['Фільтрація','Панель курсів і скидання фільтрів','TC003'],['Читання матеріалу','Картка та PDF-ридер','TC004'],['Навчальна статистика','Панель прогресу','TC005'],['Вихід','Кнопка виходу','TC006'],['Активація і доступ до профілю','API, Authentication, Firestore','IT-002'],['Блокування акаунта','Адміністративний API та правила БД','IT-003']])
 r.p('Межі: під «звітністю» перевірено відображення навчальної статистики 100% (2/2). Експорт звіту в PDF/Excel у застосунку не реалізований і не позначається як перевірений. Справжня SMTP-доставка, Google OAuth, навантаження та фізичні пристрої не входили до цього запуску.')
 r.heading('2. Функціональне тестування',2)
 r.p('Розроблено 12 сценаріїв: 6 позитивних і 6 негативних. Негативний тест має статус PASS, якщо система правильно відхилила некоректні дані. Кожен сценарій виконується в реальному інтерфейсі через JUnit/Compose UI Test.')
 rows=[]
 for id,title,kind,pre,inputs,steps,expected in cases:
  name,status=status_for(id);node=observed[name]
  actual=expected if status=='PASS' else (node.findtext('failure') or node.findtext('error') or 'Тест не завершено')[:500]
  rows.append([id,title,expected,actual,status])
  r.heading(id+' — '+title,3)
  r.p('Тип: '+kind+'. Передумови: '+pre)
  r.p('Вхідні дані: '+inputs)
  r.p('Кроки: '+steps)
  r.p('Очікуваний результат: '+expected)
  r.p('Фактичний результат: '+actual+' Статус: '+status+'.')
 r.heading('Таблиця фактичних результатів',3)
 r.table(['ID','Тест-кейс','Очікуваний результат','Фактичний результат','Статус'],rows)
 for prefix,caption in [('TC001','Успішний вхід і каталог'),('TC002','Пошук SQL'),('TC004','Відкритий PDF англійської'),('TC005','Навчальна статистика'),('TC007','Відхилення неправильного пароля'),('TC011','Перевірка слабкого пароля')]:
  name,status=status_for(prefix);r.image(EVIDENCE/'lab11'/f'{name}_{status}.png',prefix+' — '+caption,width=7.2)
 r.heading('3. Модульне тестування',2)
 r.p('Для перевірки серверного модуля crypto.mjs використано вбудований фреймворк node:test та assert. Модуль містить passwordError, totp, matchTotp, encrypt/decrypt і captchaPng. Виконано п’ять окремих Unit-тестів; кожен містить перевірки граничних або негативних випадків.')
 r.table(['ID','Що перевірено','Очікування і результат'],[
 ['UT-001','TOTP за шістьма офіційними векторами RFC 6238','Збіг кодів із еталонами — PASS'],['UT-002','Повторний, прострочений TOTP і неправильний формат','Коди відхиляються — PASS'],['UT-003','Межі довжини, категорії символів, поширені паролі','Слабкі відхиляються; допустимі приймаються — PASS'],['UT-004','AES-GCM: правильний ключ, інший UID та інший ключ','Відновлення лише з правильними параметрами — PASS'],['UT-005','PNG CAPTCHA та відсутність відкритої відповіді','PNG має очікувані розміри; текст відповіді відсутній — PASS']])
 r.code('node --test --test-reporter=tap tools/demo/security/crypto.test.mjs')
 r.p('Фактичний результат: tests 5, pass 5, fail 0. Оригінальний протокол: results/unit-tests.tap. Код тестів: tools/demo/security/crypto.test.mjs.')
 r.heading('Приклад реалізації та коду Unit-тестів',3)
 crypto=(ROOT/'tools/demo/security/crypto.mjs').read_text(encoding='utf-8')
 r.code(crypto[crypto.index('export function passwordError'):crypto.index('\nconst alphabet')])
 unit_source=(ROOT/'tools/demo/security/crypto.test.mjs').read_text(encoding='utf-8')
 begin=unit_source.index("test('password policy")
 end=unit_source.index("test('TOTP secrets",begin)
 r.code(unit_source[begin:end].strip())
 r.image(EVIDENCE/'unit-tests.png','Test Execution Report: фактичний вивід модульних тестів у HTML-перегляді',width=16)
 r.heading('4. Інтеграційне тестування',2)
 r.p('Компоненти: REST API безпеки → Firebase Authentication → Cloud Firestore; для перевірки обмеження доступу також використано клієнтський Firestore REST-запит. Тестова CAPTCHA підготовлена довіреним Admin SDK як fixture; це не перевірка розпізнавання картинки людиною. У протоколі не зберігаються паролі, токени сесій або посилання активації.')
 it_steps=[('Сервери доступні; тестова пошта ще не зареєстрована.','ПІБ: Іван Петренко (ЛР11); група ІПЗ-21; унікальна університетська пошта.','Надіслати /api/register із правильною CAPTCHA; прочитати Auth-запис і pending_profiles за UID.','Створено неактивований Auth-акаунт і профіль із правильними ПІБ та групою.'),('IT-001 завершено; тестовий лист доступний у локальній outbox.','Одноразовий токен із тестового листа.','Активувати акаунт; увійти через API; прочитати users/{uid} і перевірити видалення pending_profiles.','Пошта підтверджена; users містить профіль; його читання повертає HTTP 200.'),('Тестовий студент увійшов; адміністратор авторизований.','UID студента та blocked=true.','Заблокувати студента; прочитати security_accounts та Auth; виконати запит до профілю зі старим токеном; розблокувати.','Стан blocked=true, Auth disabled=true, версія збільшена, старому токену відмовлено HTTP 403; розблокування працює.')]
 for case,(pre,inputs,steps,expected) in zip(data['cases'],it_steps):
  r.heading(case['id']+' — '+case['title'],3);r.p('Передумови: '+pre);r.p('Вхідні дані: '+inputs);r.p('Кроки: '+steps);r.p('Очікуваний результат: '+expected);r.p('Фактичний результат: '+json.dumps(case.get('details',{}),ensure_ascii=False)+'. Статус: '+case['status'])
 r.code('node tools/demo/lab11-integration.mjs')
 r.image(EVIDENCE/'firestore-student.png','Фактичний результат створення студента та читання документа Firestore; знімок сторінки протоколу',width=16)
 r.image(EVIDENCE/'firestore-console.png','Скриншот Firebase Emulator Suite: створений студент Іван Петренко (ЛР11) у колекції users. Після фіксації тестовий акаунт видалено.',width=16)
 r.heading('5. Виявлені дефекти та повторне тестування',2)
 r.table(['ID / тип','Кроки та фактична проблема','Очікувана поведінка','Рішення / стан'],[
 ['DEF-001 / тестова інфраструктура','Запуск Compose UI Test на API 37 з Espresso 3.5.1: NoSuchMethodException InputManager.getInstance. 12 тестів не змогли розпочати функціональні перевірки.','Тестовий рушій запускає сценарії на емуляторі.','Espresso оновлено до 3.7.0, AndroidX JUnit — до 1.3.0. Початковий XML збережено; повторний запуск можливий.'],
 ['DEF-002 / інтерфейс, середня важливість','Увійти → відкрити фільтри → натиснути системну «Назад». Застосунок завершував Activity; тест TC003 отримав NoActivityResumedException.','Закрити панель фільтрів і залишитися в каталозі.','Додано BackHandler для відкритої панелі. Результат повторного TC003: '+status_for('TC003')[1]+'.'],
 ['TEST-001 / код нових тестів','Пошук лише за SetText знаходив два поля: каталог і пошук прихованої панелі. Точне порівняння помилки входу не враховувало префікс ⚠️.','Тест однозначно знаходить потрібний елемент і повідомлення.','Уточнено селектор за contentDescription та пошук тексту за підрядком. Це помилки тестового коду, а не окремі дефекти продукту.']])
 r.p('Докази початкових збоїв збережено у results/ui-initial-infrastructure-failure.xml та results/ui-selector-initial-run.xml. Фінальний протокол — results/ui-final.xml. Наявність початкового FAIL не прихована: після змін виконано повторне тестування та весь набір функціональних сценаріїв.')
 r.p('Офіційне джерело оновлення тестових бібліотек: https://developer.android.com/jetpack/androidx/releases/test. Espresso 3.7.0 використовує getSystemService замість застарілого рефлексивного InputManager.getInstance.')
 r.heading('6. Підсумкові результати',2)
 passed=sum(status_for(c[0])[1]=='PASS' for c in cases)
 r.table(['Набір','Виконано','PASS','FAIL'],[['Функціональні UI-сценарії',12,passed,12-passed],['Модульні тести',5,5,0],['Сценарії інтеграції ЛР11',len(data['cases']),sum(c['status']=='PASS' for c in data['cases']),sum(c['status']!='PASS' for c in data['cases'])]])
 r.p('Додатково виконано наявний регресійний набір: 53 перевірки API безпеки та 31 перевірка авторизації, Storage й реакцій. Це кількість перевірок у скриптах, а не додаткові окремі UI-тест-кейси; протоколи збережено в results/security-api.txt та results/firebase-access.txt.')
 r.heading('Висновок',2)
 r.p(f'Під час лабораторної роботи визначено функції «Stefanyk.Hub», розроблено й виконано 12 функціональних сценаріїв (6 позитивних та 6 негативних), 5 модульних тестів і 3 інтеграційні сценарії. У фінальному запуску функціональні результати: {passed}/12 PASS. Перевірено роботу входу, пошуку, фільтрів, PDF-ридера, статистики й валідації, а також взаємодію API з Firebase. Виявлено та виправлено дефект оброблення кнопки «Назад» у фільтрах і несумісність тестових бібліотек. Результати підтверджено протоколами та скриншотами. Отримані результати стосуються перевірених сценаріїв локальної debug-збірки й не доводять відсутності всіх можливих дефектів.')
 r.heading('Відтворення перевірок',2)
 r.code('.\\tools\\demo\\start.cmd\nnode --test tools/demo/security/crypto.test.mjs\nnode tools/demo/lab11-integration.mjs\n.\\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.stefanykhub.Lab11FunctionalTest')
 r.p('Якщо сервери вже працюють, повторно start.cmd не запускайте. Скрипт build-lab11-report.py формує DOCX, Markdown і HTML за збереженими протоколами, не підмінюючи фактичні статуси.')
 r.save();print('Generated Word, Markdown and HTML lab report.')

main()
