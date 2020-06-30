#include <chrono>
#include <iostream>
#include <vector>

#include <cassert>

constexpr auto WIDTH = 320;
constexpr auto HEIGHT = 280;

constexpr float deg2rad = M_PI * 2.0f / 360.0f;
constexpr float rad2deg = 1.0f / deg2rad;

namespace foo::bar
{

class Baz
{
  public:
	Baz() = default;
	Baz(int x) : x(x) {}
	~Baz() {}

	void something([[maybe_unused]] float x)
	{
		this->x = x;
	}

  private:
	int x = 42;
	bool b = true;
};

} // namespace foo::bar

using Clock = std::chrono::high_resolution_clock;

void something_else()
{
	using namespace std::chrono;

	const auto t1 = Clock::now();

	foo::bar::Baz b;
	b.something(42);

	const auto t2 = Clock::now();

	std::cout << "Elapsed: " << duration_cast<milliseconds>(t2 - t1).count() << "\n";
}

template <typename T> struct Camera {
	Mat44<T> view;
	Mat44<T> project;
};

Camera<float> default_camera()
{
	Camera<float> c;
#ifdef VIEW
	c.project = {
	    // clang-format off
		 2.11244, -0      ,  0     , -0,
		-0      , -2.41421, -0     ,  0,
		 0      , -0      , -1.001 , -1,
		-0      ,  0      , -0.1001,  0,
	    // clang-format on
	};
#else
	c.view = {
	    // clang-format off
		-1          , -3.55271e-15, -8.74228e-08,  0,
		-3.55271e-15,  1          , -1.55294e-22,  0,
		 8.74228e-08,  1.55294e-22, -1          , -0,
		 4.44132    , -2.04038    , -34.272     ,  1,
	    // clang-format on
	};
#endif

	return c;
}

enum class Result { Ok, Fail };

int control()
{
	bool b = true;

	if (b) {
		std::cout << "True\n";
	} else {
		std::cout << "False\n";
	}

	for (int i = 0; i < 10; i++) {
		std::cout << " " << i;
		continue;
	}
	std::cout << "\n";

	const std::vector<int> xs = {1, 2, 3, 4};
	for (const auto &x : xs) {
		std::cout << " " << x;
		break;
	}
	std::cout << "\n";

	const auto result = Result::Fail;

	switch (result) {
	case Result::Ok:
		std::cout << "Ok\n";
		break;
	case Result::Fail:
		std::cout << "Fail\n";
		break;
	default:
		assert(false);
		break;
	}

	return 21;
}

template <typename T> struct Vec3 {
	T x = 0;
	T y = 0;
	T z = 0;

	T Distance() const
	{
		return std::sqrt(DistanceSquared());
	}

	T DistanceSquared() const
	{
		return x * x + y * y + z * z;
	}

	static constexpr Vec3<T> zero = {0, 0, 0};
	static constexpr Vec3<T> one = {1, 1, 1};

	static constexpr Vec3<T> up = {0, 0, 1};
	static constexpr Vec3<T> right = {1, 0, 0};
	static constexpr Vec3<T> forward = {0, 1, 0};
};

struct Quaternion {
	float x = 0.0f;
	float y = 0.0f;
	float z = 0.0f;
	float w = 1.0f;

	Quaternion() = default;

	Quaternion(float rotation_x, float rotation_y, float rotation_z)
	{
		// https://en.wikipedia.org/wiki/Conversion_between_quaternions_and_Euler_angles#Source_Code

		float cy = cos(rotation_z * 0.5);
		float sy = sin(rotation_z * 0.5);
		float cp = cos(rotation_y * 0.5);
		float sp = sin(rotation_y * 0.5);
		float cr = cos(rotation_x * 0.5);
		float sr = sin(rotation_x * 0.5);

		w = cr * cp * cy + sr * sp * sy;
		x = sr * cp * cy - cr * sp * sy;
		y = cr * sp * cy + sr * cp * sy;
		z = cr * cp * sy - sr * sp * cy;
	}
};

template <typename T> Vec3<T> operator*(const Quaternion &q, const Vec3<T> &v_t)
{
	const auto v = Vec3<float>{(float)v_t.x, (float)v_t.y, (float)v_t.z};
	const auto u = Vec3<float>{q.x, q.y, q.z};
	const auto result = 2.0f * dot(u, v) * u + (q.w * q.w - dot(u, u)) * v + 2.0f * q.w * cross(u, v);
	return Vec3<T>{T(result.x), T(result.y), T(result.z)};
}
