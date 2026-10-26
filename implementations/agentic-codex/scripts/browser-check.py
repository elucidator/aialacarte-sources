#!/usr/bin/env python3
"""Visible browser checks; requires Playwright and a local Chrome/Chromium installation."""
import argparse
import os
from pathlib import Path
from process_check_import import Apps, api
from playwright.sync_api import sync_playwright, expect


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('workspace', nargs='?', default=str(Path(__file__).resolve().parents[1]))
    parser.add_argument('--available-only', action='store_true')
    parser.add_argument('--screenshots', default='verification-output/screenshots')
    args = parser.parse_args()
    screenshots = Path(args.screenshots); screenshots.mkdir(parents=True, exist_ok=True)
    with Apps(args.workspace, count=1) as apps, sync_playwright() as p:
        url = apps.urls[0]; assert api(url,'/api/demo/reset','POST',{})[0] == 200
        browser = p.chromium.launch(channel=os.environ.get('BROWSER_CHANNEL','chrome'), headless=True)
        page = browser.new_page(viewport={'width':1440,'height':1000})
        errors = []; page.on('pageerror', lambda e: errors.append(str(e)))
        page.goto(url); expect(page.locator('#sessions tr')).to_have_count(3)
        expect(page.locator('#status')).to_contain_text('Ready for Alice')
        expect(page.locator('#sessions tr').nth(1)).to_contain_text('19')
        expect(page.locator('#sessions tr').nth(2).get_by_role('button')).to_be_disabled()
        # Accept the request on the server, then lose its response at the browser boundary.
        intercepted = []
        def lose_response(route):
            if route.request.method != 'POST' or intercepted:
                route.continue_(); return
            response = page.request.post(route.request.url, headers=route.request.all_headers(), data=route.request.post_data)
            assert response.status == 201
            intercepted.append(response.json())
            route.abort('failed')
        page.route('**/api/bookings', lose_response)
        page.locator('#sessions tr').first.get_by_role('button',name='Book',exact=True).click()
        expect(page.locator('#status')).to_have_class('error')
        page.unroute_all(behavior='wait')
        assert len(api(url,'/api/bookings')[1]) == 1
        page.get_by_role('button',name='My bookings',exact=True).click()
        page.get_by_role('button',name='Cancel',exact=True).click()
        expect(page.locator('#status')).to_contain_text('Booking cancelled')
        page.get_by_role('button',name='Sessions',exact=True).click()
        expect(page.locator('#status')).to_contain_text('Ready for Alice')
        page.locator('#sessions tr').first.get_by_role('button',name='Book',exact=True).click()
        expect(page.locator('#status')).to_contain_text('Your place is booked')
        active = api(url,'/api/bookings')[1]
        assert len(active) == 1 and active[0]['id'] != intercepted[0]['id']
        api(url,'/api/demo/reset','POST',{})
        page.reload(); expect(page.locator('#status')).to_contain_text('Ready for Alice')
        page.screenshot(path=str(screenshots/'sessions.png'), full_page=True)
        if args.available_only:
            page.get_by_label('Only sessions with available places').check()
            expect(page.locator('#sessions tr')).to_have_count(2)
            expect(page.locator('#page-label')).to_contain_text('2 sessions')
        page.locator('#sessions tr').nth(1).get_by_role('button', name='Book', exact=True).click()
        expect(page.locator('#status')).to_contain_text('Your place is booked')
        if args.available_only: expect(page.locator('#sessions tr')).to_have_count(1)
        else: expect(page.locator('#sessions tr').nth(1).get_by_role('button')).to_have_text('Booked')
        page.get_by_role('button', name='My bookings', exact=True).click()
        expect(page.locator('#bookings')).to_contain_text('Practical Kotlin')
        page.screenshot(path=str(screenshots/'bookings.png'), full_page=True)
        page.get_by_role('button', name='Cancel', exact=True).click()
        expect(page.locator('#status')).to_contain_text('Booking cancelled')
        expect(page.locator('#bookings')).to_contain_text('no active bookings')
        page.get_by_role('button', name='Sessions', exact=True).click()
        expect(page.locator('#sessions tr')).to_have_count(2 if args.available_only else 3)
        page.locator('#identity').select_option('bob')
        expect(page.locator('#status')).to_contain_text('Ready for Bob')
        page.locator('#sessions tr').first.get_by_role('button',name='Book',exact=True).click()
        expect(page.locator('#status')).to_contain_text('Your place is booked')
        page.locator('#identity').select_option('alice')
        expect(page.locator('#status')).to_contain_text('Ready for Alice')
        page.get_by_role('button', name='My bookings', exact=True).click()
        expect(page.locator('#bookings')).to_contain_text('no active bookings')
        # Force an actual error from a changed capacity after a rendered session list.
        page.get_by_role('button',name='Sessions',exact=True).click()
        expect(page.locator('#status')).to_contain_text('Ready for Alice')
        assert api(url,'/api/bookings','POST',{'sessionId':2},'bob','external')[0] == 201
        page.locator('#sessions tr').nth(1).get_by_role('button',name='Book',exact=True).click()
        expect(page.locator('#status')).to_have_class('error')
        expect(page.locator('#status')).to_contain_text('no available places')
        page.screenshot(path=str(screenshots/'error.png'),full_page=True)
        page.goto(url+'/demo.html'); expect(page.locator('#controls')).to_be_visible()
        page.locator('#mode').select_option('lost-ack')
        expect(page.locator('#status')).to_contain_text('Gateway mode updated')
        page.locator('#dispatch').click(); expect(page.locator('#status')).to_contain_text('Dispatch finished')
        expect(page.locator('#attempts')).to_contain_text('TIMEOUT')
        expect(page.locator('#accepted')).to_contain_text('confirmation:')
        page.locator('#mode').select_option('normal'); expect(page.locator('#status')).to_contain_text('Gateway mode updated')
        page.locator('#dispatch').click(); expect(page.locator('#status')).to_contain_text('Dispatch finished')
        expect(page.locator('#events')).to_contain_text('ACKNOWLEDGED')
        page.screenshot(path=str(screenshots/'delivery.png'),full_page=True)
        # Observe loading through a bounded network gate.
        page.goto(url)
        expect(page.locator('#status')).to_contain_text('Ready for Alice')
        held_routes = []
        page.route('**/api/sessions?*', lambda route: held_routes.append(route))
        page.locator('#identity').select_option('bob')
        expect(page.locator('#status')).to_contain_text('Loading')
        assert held_routes
        for route in held_routes: route.continue_()
        expect(page.locator('#status')).to_contain_text('Ready for Bob')
        page.unroute_all(behavior='wait')
        assert not errors, errors
        browser.close(); api(url,'/api/demo/reset','POST',{})
    print('PASS: browser listing, identities, booking, cancellation, response-loss/cancel/fresh-key regression, errors, loading, delivery panel'+(' and availability checkbox' if args.available_only else ''))

if __name__ == '__main__': main()
