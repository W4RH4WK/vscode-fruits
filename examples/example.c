#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define some_macro(a, b, c)                                                                                            \
	do {                                                                                                               \
		foo(a);                                                                                                        \
		bar(b);                                                                                                        \
		baz(c);                                                                                                        \
	} while (0)

typedef enum Result {
	RESULT_OK,
	RESULT_GENERAL_ERROR,
	RESULT_UNKNOWN_ERROR,
} Result;

const char *result_string(Result result)
{
	switch (result) {
	case RESULT_OK:
		return "Ok";
	case RESULT_GENERAL_ERROR:
		return "General Error";
	case RESULT_UNKNOWN_ERROR:
		return "Unknown Error";
	}

	return "Invalid Result";
}

typedef struct Vec3f {
	float x, y, z;
} Vec3f;

const Vec3f VEC3_ZERO = {0, 0, 0};
const Vec3f VEC3_ONE = {1, 1, 1};

Vec3f vec3f_add(Vec3f a, Vec3f b)
{
	return (Vec3f){
	    a.x + b.x,
	    a.y + b.y,
	    a.z + b.z,
	};
}

void vec3f_fprint(FILE *out, Vec3f v)
{
	fprintf(out, "Vec3f{%f, %f, %f}", v.x, v.y, v.z);
}

void vec3f_print(Vec3f v)
{
	vec3f_fprint(stdout, v);
}

int main(void)
{
	Vec3f some_point = {1, 2, 3};

	vec3f_print(vec3f_add(some_point, VEC3_ONE));
	printf("\n");

	return EXIT_SUCCESS;
}
