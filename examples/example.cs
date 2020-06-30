using UnityEngine;
using UnityEngine.Assertions;

[RequireComponent(typeof(Controls))]
public class FlightModel : MonoBehaviour
{
    // Change in rotation expressed in Euler angles.
    public Vector3 DeltaRotation { get; private set; }

    Vector3 DeltaRotationVelocity;

    void UpdateRotation()
    {
        var rates = FlightModelParams.PitchYawRollRate;
        {
            if (Controls.PitchYawRoll.x < 0.0f)
                rates.x *= FlightModelParams.PitchUpRateModifier;

            if (Controls.FocusMode)
                rates = FlightModelParams.FocusPitchYawRollRate * Vector3.one;
            else if (Controls.StrafeMode)
                rates *= FlightModelParams.StrafePitchYawRollRateModifier;
            else if (Controls.HighGTurnMode)
                rates.x *= FlightModelParams.HighGTurnPitchRateModifier;
        }

        float mobility = 1.0f;
        {
            var mobilitySlope = (FlightModelParams.Mobility - 1) / (FlightModelParams.MaxSpeed - FlightModelParams.BaseThrust);
            mobility = mobilitySlope * Speed + 1 - mobilitySlope * FlightModelParams.BaseThrust;

            if (Controls.StrafeMode)
                mobility *= FlightModelParams.StrafePitchYawRollRateModifier;
        }

        var responseRate = mobility * FlightModelParams.PitchYawRollResponseRate;

        var responseMaxSpeed = mobility * FlightModelParams.PitchYawRollResponseMaxSpeed;
        if (Controls.FocusMode)
            responseMaxSpeed = FlightModelParams.FocusPitchYawRollResponseMaxSpeed * Vector3.one;

        var newDeltaRotation = Vector3.Scale(rates, Controls.PitchYawRoll);
        DeltaRotation = new Vector3(
            Mathf.SmoothDamp(DeltaRotation.x, newDeltaRotation.x, ref DeltaRotationVelocity.x, 1.0f / responseRate.x, responseMaxSpeed.x),
            Mathf.SmoothDamp(DeltaRotation.y, newDeltaRotation.y, ref DeltaRotationVelocity.y, 1.0f / responseRate.y, responseMaxSpeed.y),
            Mathf.SmoothDamp(DeltaRotation.z, newDeltaRotation.z, ref DeltaRotationVelocity.z, 1.0f / responseRate.z, responseMaxSpeed.z)
        );

        transform.Rotate(DeltaRotation * Time.deltaTime);

        // Simulate rotation induced by lift when banking.
        if (!Controls.FocusMode)
        {
            var lateralDrift = FlightModelParams.BankingDriftRate * Mathf.Sin(-transform.eulerAngles.z * Mathf.Deg2Rad) * Time.deltaTime;
            transform.Rotate(0.0f, lateralDrift, 0.0f, Space.World);

            var liftLoss = FlightModelParams.BankingLiftLossRate * Mathf.Sin(0.5f * transform.eulerAngles.z * Mathf.Deg2Rad) * Time.deltaTime;
            transform.rotation *= Quaternion.Euler(-liftLoss, 0.0f, 0.0f);
        }
    }

    //////////////////////////////////////////////////////////////////////////

    public Vector3 Velocity { get; private set; }

    public float Speed => Velocity.magnitude;

    float Thrust;

    void UpdateVelocity()
    {
        var factor = Controls.Throttle > 0.0f ? FlightModelParams.Acceleration : FlightModelParams.Deceleration;
        var thrust = factor * Controls.Throttle;

        if (Controls.HighGTurnMode)
            thrust *= FlightModelParams.HighGTurnDecelerationModifier;

        var newThrust = FlightModelParams.BaseThrust + thrust;
        Thrust = Mathf.Lerp(Thrust, newThrust, FlightModelParams.ThrustResponseRate * Time.deltaTime);

        // Simulate speed up / slow down induced by gravity when pitching.
        var gravitationalThrust = FlightModelParams.FlightGravity * Mathf.Sin(transform.eulerAngles.x * Mathf.Deg2Rad);

        var responseRate = FlightModelParams.VelocityVectorResponseRate;

        if (Controls.StrafeMode)
            responseRate = FlightModelParams.StrafeVelocityVectorResponseRate;

        var newVelocity = (Thrust + gravitationalThrust) * transform.forward;
        Velocity = Vector3.Lerp(Velocity, newVelocity, responseRate * Time.deltaTime);
        Velocity = Vector3.ClampMagnitude(Velocity, FlightModelParams.MaxSpeed);

        transform.position += Velocity * Time.deltaTime;
    }

    //////////////////////////////////////////////////////////////////////////

    public bool Stalling { get; private set; } = false;

    float StallingDurationLeft = 0.0f;

    void UpdateStalling()
    {
        if (!Stalling && Speed < FlightModelParams.StallingAttackSpeed)
        {
            Stalling = true;
            StallingDurationLeft = FlightModelParams.StallingMinimumDuration;

            Thrust *= FlightModelParams.StallingThrustCutFactor;
        }

        if (Stalling)
        {
            var newRotation = Quaternion.LookRotation(Vector3.down, transform.up);
            transform.rotation = Quaternion.Lerp(transform.rotation, newRotation, FlightModelParams.StallingRotationRate * Time.deltaTime);

            Velocity += FlightModelParams.StallingGravity * Vector3.down * Time.deltaTime;
            transform.position += Velocity * Time.deltaTime;

            StallingDurationLeft -= Time.deltaTime;
        }

        if (Stalling && Speed > FlightModelParams.StallingReleaseSpeed && StallingDurationLeft <= 0.0f)
            Stalling = false;
    }

    //////////////////////////////////////////////////////////////////////////

    Controls Controls;

    public FlightModelParameters FlightModelParams { get; private set; }

    void Start()
    {
        Controls = GetComponent<Controls>();
        Assert.IsNotNull(Controls);

        FlightModelParams = GetComponentInChildren<FlightModelParameters>();
        Assert.IsNotNull(FlightModelParams);

        Thrust = FlightModelParams.BaseThrust;
        Velocity = Thrust * transform.forward;
    }

    void Update()
    {
        //UpdateStalling();

        if (!Stalling)
        {
            UpdateRotation();

            UpdateVelocity();
        }
    }
}
