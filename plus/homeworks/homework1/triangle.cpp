#include <iostream>
#include <cmath>
#include <iomanip>

using namespace std;

int main()
{
    float xa, ya, xb, yb, xc, yc;

    cout << "Введите координаты точки A (x y): ";
    cin >> xa >> ya;
    cout << "Введите координаты точки B (x y): ";
    cin >> xb >> yb;
    cout << "Введите координаты точки C (x y): ";
    cin >> xc >> yc;

    float ab = sqrt(pow(xb - xa, 2) + pow(yb - ya, 2));
    float bc = sqrt(pow(xc - xb, 2) + pow(yc - yb, 2));
    float ac = sqrt(pow(xc - xa, 2) + pow(yc - ya, 2));

    float perimeter = ab + bc + ac;

    float s = perimeter / 2;

    float area = sqrt(s * (s - ab) * (s - bc) * (s - ac));

    cout << fixed << setprecision(2);
    cout << "Периметр треугольника: " << perimeter << endl;
    cout << "Площадь треугольника: " << area << endl;

    return 0;
}