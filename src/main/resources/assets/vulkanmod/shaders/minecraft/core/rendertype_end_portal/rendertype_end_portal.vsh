#version 450

#include "projection.glsl"

layout(location = 0) in vec3 Position;

layout(binding = 0) uniform UniformBufferObject {
   mat4 MVP;
};

layout(location = 0) out vec4 texProj0;

void main() {
    gl_Position = MVP * vec4(Position, 1.0);

    texProj0 = projection_from_position(gl_Position);
}

/*
#version 150

#moj_import <projection.glsl>

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 texProj0;
*/


