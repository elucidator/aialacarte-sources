#!/usr/bin/env python3
"""Generate presenter-only patches from untouched exports; never edits the baseline."""
import difflib
import importlib.util
from pathlib import Path
import tempfile

HERE = Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('exporter',HERE/'export.py')
module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)


def reference(number, root):
    edit = lambda name,old,new: module.mutate(root/name,old,new)
    prefix='src/main/kotlin/conference/'
    if number == 1:
        edit(prefix+'ApiController.kt',
            '''fun sessions(@RequestParam(required=false) page: String?, @RequestParam(required=false) size: String?) =
        service.sessions(integer(page, 0, "page"), integer(size, 20, "size"))''',
            '''fun sessions(@RequestParam(required=false) page: String?, @RequestParam(required=false) size: String?,
                 @RequestParam(required=false) availableOnly: String?): SessionPage {
        val filter = when (availableOnly) {
            null, "false" -> false
            "true" -> true
            else -> badInput("availableOnly must be true or false.")
        }
        return service.sessions(integer(page, 0, "page"), integer(size, 20, "size"), filter)
    }''')
        edit(prefix+'BookingService.kt','fun sessions(page: Int, size: Int): SessionPage','fun sessions(page: Int, size: Int, availableOnly: Boolean = false): SessionPage')
        edit(prefix+'BookingService.kt','return repository.sessions(page, size)','return repository.sessions(page, size, availableOnly)')
        edit(prefix+'PostgresBookingRepository.kt','fun sessions(page: Int, size: Int): SessionPage','fun sessions(page: Int, size: Int, availableOnly: Boolean = false): SessionPage')
        edit(prefix+'PostgresBookingRepository.kt',
            '), selected AS (SELECT * FROM counts ORDER BY id LIMIT ? OFFSET ?)\n            SELECT selected.*, (SELECT count(*) FROM counts) AS total',
            '), eligible AS (SELECT * FROM counts WHERE ? = false OR active < capacity),\n            selected AS (SELECT * FROM eligible ORDER BY id LIMIT ? OFFSET ?)\n            SELECT selected.*, (SELECT count(*) FROM eligible) AS total')
        edit(prefix+'PostgresBookingRepository.kt','""".trimIndent(), size, page.toLong() * size)',
            '""".trimIndent(), availableOnly, size, page.toLong() * size)')
        edit('src/main/resources/static/index.html','<section id="sessions-view"><h2>Conference sessions</h2>',
            '<section id="sessions-view"><h2>Conference sessions</h2>\n<label><input type="checkbox" id="available-only"> Only sessions with available places</label>')
        edit('src/main/resources/static/app.js','`/api/sessions?page=${page}&size=${size}`',
            '`/api/sessions?page=${page}&size=${size}&availableOnly=${$("available-only").checked}`')
        edit('src/main/resources/static/app.js',"busy = value; $('identity').disabled = value;",
            "busy = value; $('identity').disabled = value; $('available-only').disabled = value;")
        edit('src/main/resources/static/app.js',"$('identity').onchange = () => { page = 0; refresh(); };",
            "$('identity').onchange = () => { page = 0; refresh(); };\n$('available-only').onchange = () => { page = 0; refresh(); };")
    elif number == 2:
        edit(prefix+'PostgresBookingRepository.kt','SELECT capacity FROM sessions WHERE id=?"','SELECT capacity FROM sessions WHERE id=? FOR UPDATE"')
    elif number == 3:
        (root/(prefix+'BookingRepository.kt')).write_text('''package conference

interface BookingRepository {
    fun sessions(page: Int, size: Int): SessionPage
    fun book(attendee: String, key: String, sessionId: Long): BookingResult
    fun bookings(attendee: String): List<Booking>
    fun cancel(attendee: String, id: String)
}
''')
        edit(prefix+'BookingService.kt','private val repository: PostgresBookingRepository','private val repository: BookingRepository')
        edit(prefix+'PostgresBookingRepository.kt','manager: PlatformTransactionManager) {','manager: PlatformTransactionManager) : BookingRepository {')
        for method in ['sessions','book','bookings','cancel']:
            edit(prefix+'PostgresBookingRepository.kt',f'    fun {method}(',f'    override fun {method}(')
    elif number == 4:
        edit(prefix+'ConfirmationDispatcher.kt','val deliveryKey = java.util.UUID.randomUUID().toString()', 'val deliveryKey = row["delivery_key"] as String')
    elif number == 5:
        edit('compose.yaml','http://localhost:8080/status','http://localhost:8080/health')


def main():
    for number in range(1,6):
        with tempfile.TemporaryDirectory(prefix=f'conference-ref-{number}-') as directory:
            root=Path(directory)/'workspace'; module.export(number,root)
            before={str(p.relative_to(root)):p.read_text() for p in root.rglob('*') if p.is_file() and p.suffix in ('.kt','.js','.html','.yaml')}
            reference(number,root)
            after={str(p.relative_to(root)):p.read_text() for p in root.rglob('*') if p.is_file() and p.suffix in ('.kt','.js','.html','.yaml')}
            diff=''
            for name in sorted(set(before)|set(after)):
                if before.get(name) != after.get(name):
                    diff+=''.join(difflib.unified_diff(before.get(name,'').splitlines(True),after.get(name,'').splitlines(True),
                        fromfile='a/'+name if name in before else '/dev/null',tofile='b/'+name))
            (HERE/f'reference-patches/exercise-{number}.patch').write_text(diff)
            print(f'Prepared reference {number}')

if __name__=='__main__':main()
