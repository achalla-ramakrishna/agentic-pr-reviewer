import { expect, test, type Page } from '@playwright/test';

const flight = {
  id: 'f1', flightNumber: 'SR101', origin: 'DFW', destination: 'JFK',
  departureTime: '2026-11-01T07:00:00Z', arrivalTime: '2026-11-01T10:35:00Z', durationMinutes: 215,
  aircraftType: 'B738', status: 'SCHEDULED', cabin: 'ECONOMY', seatsAvailable: 42, fare: 189, currency: 'USD',
};

async function stubLogin(page: Page) {
  await page.route('**/api/v1/auth/login', async (route) => {
    const body = route.request().postDataJSON();
    if (body.password !== 'agent123') {
      return route.fulfill({ status: 401, json: { status: 401, detail: 'Invalid credentials', errorCode: 'UNAUTHORIZED', correlationId: 'cid-123' } });
    }
    return route.fulfill({ json: { accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 3600, username: 'agent', roles: ['AGENT'] } });
  });
}

test('shows the server error message on bad credentials', async ({ page }) => {
  await stubLogin(page);
  await page.goto('/login');
  await page.getByLabel('Username').fill('agent');
  await page.getByLabel('Password').fill('wrong');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('alert')).toContainText('Invalid credentials');
  await expect(page.getByRole('alert')).toContainText('cid-123');
});

test('agent searches, books and gets a record locator', async ({ page }) => {
  await stubLogin(page);
  await page.route('**/api/v1/flights/search?*', (route) => route.fulfill({ json: [flight] }));
  await page.route('**/api/v1/bookings', async (route) => {
    expect(route.request().headers()['authorization']).toBe('Bearer jwt');
    const body = route.request().postDataJSON();
    expect(body.segments).toEqual([{ flightId: 'f1', cabin: 'ECONOMY' }]);
    await route.fulfill({
      status: 201,
      json: { recordLocator: 'K7QXPM', status: 'CONFIRMED', contactEmail: body.contactEmail, passengers: body.passengers,
        segments: [], totalAmount: 189, currency: 'USD', createdAt: new Date().toISOString() },
    });
  });

  await page.goto('/login');
  await page.getByLabel('Username').fill('agent');
  await page.getByLabel('Password').fill('agent123');
  await page.getByRole('button', { name: 'Sign in' }).click();

  await page.getByRole('button', { name: 'Search' }).click();
  await expect(page.getByTestId('flight-row')).toHaveCount(1);
  await page.getByRole('button', { name: 'Book' }).click();

  await page.getByLabel('First name').fill('Ana');
  await page.getByLabel('Last name').fill('Diaz');
  await page.getByLabel('Email').fill('ana@example.com');
  await page.getByRole('button', { name: 'Confirm booking' }).click();

  await expect(page.getByTestId('pnr')).toHaveText('K7QXPM');
});

test('booking pages redirect anonymous users to sign in', async ({ page }) => {
  await page.goto('/bookings');
  await expect(page).toHaveURL(/\/login$/);
});
