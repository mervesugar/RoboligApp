package com.robolig.controller.core

object AppConstants {
    const val APPLICATION_NAME = "Robolig Controller"
    const val PROTOCOL_VERSION = "1.0"
    const val USB_BAUD_RATE = 115_200
    const val DEFAULT_USB_WRITE_TIMEOUT_MS = 250
    const val DEFAULT_USB_READ_TIMEOUT_MS = 100
}

object ControlLoopConstants {
    const val VEHICLE_CONTROL_HZ = 60
    const val ARM_CONTROL_HZ = 30
    const val TELEMETRY_HZ = 10
    const val HEARTBEAT_INTERVAL_MS = 500L
    const val WATCHDOG_TIMEOUT_MS = 2_000L
    const val VEHICLE_CONTROL_PERIOD_MS = 1_000L / VEHICLE_CONTROL_HZ
    const val ARM_CONTROL_PERIOD_MS = 1_000L / ARM_CONTROL_HZ
    const val TELEMETRY_PERIOD_MS = 1_000L / TELEMETRY_HZ
}

object ProtocolConstants {
    const val PACKET_SIZE_BYTES = 32
    const val PAYLOAD_SIZE_BYTES = 24
    const val HEADER = 0xAA
    const val TIMESTAMP_MASK = 0x00FF_FFFF
    const val MIN_SIGNED_CONTROL_VALUE = -127
    const val MAX_SIGNED_CONTROL_VALUE = 127
    const val MIN_SERVO_ANGLE = 0
    const val MAX_SERVO_ANGLE = 180
    const val GRIPPER_CLOSED = 0
    const val GRIPPER_OPEN = 255
}

object ArmConstants {
    const val SHOULDER_LINK_METERS = 0.18f
    const val ELBOW_LINK_METERS = 0.17f
    const val SHOULDER_SERVO_OFFSET_DEGREES = 80f
    const val ELBOW_SERVO_OFFSET_DEGREES = 14f
    const val WRIST_PITCH_SERVO_OFFSET_DEGREES = 172f
    const val MIN_FORWARD_METERS = 0.10f
    const val MAX_FORWARD_METERS = 0.34f
    const val MIN_HEIGHT_METERS = 0.03f
    const val MAX_HEIGHT_METERS = 0.30f
    const val MIN_LATERAL_METERS = -0.18f
    const val MAX_LATERAL_METERS = 0.18f
    const val NORMAL_TRANSLATION_STEP_METERS = 0.010f
    const val PRECISION_TRANSLATION_STEP_METERS = 0.004f
    const val NORMAL_ROTATION_STEP_DEGREES = 3.5f
    const val PRECISION_ROTATION_STEP_DEGREES = 1.5f
    const val ZIPLINE_STEP = 0.03f
    const val HOME_FORWARD_METERS = 0.22f
    const val HOME_HEIGHT_METERS = 0.16f
    const val HOME_LATERAL_METERS = 0f
    const val DEFAULT_WRIST_ROLL_DEGREES = 90f
    const val DEFAULT_CLAW_ROTATION_DEGREES = 90f
    const val HOME_SHOULDER_DEGREES = 78
    const val HOME_ELBOW_DEGREES = 92
    const val HOME_WRIST_PITCH_DEGREES = 96
}

object TelemetryConstants {
    const val MAX_SPEED_METERS_PER_SECOND = 2.5f
    const val CURRENT_SCALE_AMPS_PER_LSB = 0.1f
}

object VideoConstants {
    const val CONNECT_TIMEOUT_MS = 3_000
    const val READ_TIMEOUT_MS = 5_000
    const val RECONNECT_DELAY_MS = 1_500L
    const val MAX_RECONNECT_DELAY_MS = 8_000L
    const val FRAME_HISTORY_WINDOW_MS = 1_000L
    const val INITIAL_FRAME_BUFFER_BYTES = 65_536
}

object PreferenceConstants {
    const val CONTROLLER_PREFERENCES = "robolig_controller_preferences"
    const val VIDEO_STREAM_URL = "video_stream_url"
    const val LOG_LEVEL = "log_level"
    const val SHOW_PACKETS_OVERLAY = "show_packets_overlay"
    const val USE_DEVICE_CAMERA = "use_device_camera"
    const val CUBE_DETECTION_ENABLED = "cube_detection_enabled"
    const val RFID_UID_CITY_PREFIX = "rfid_uid_"
}
