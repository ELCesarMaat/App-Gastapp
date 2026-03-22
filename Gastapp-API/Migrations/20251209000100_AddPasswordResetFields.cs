using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Gastapp_API.Migrations
{
    /// <inheritdoc />
    public partial class AddPasswordResetFields : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "PasswordResetCodeHash",
                table: "Users",
                type: "text",
                nullable: true);

            migrationBuilder.AddColumn<DateTime>(
                name: "PasswordResetCodeExpiresAt",
                table: "Users",
                type: "timestamp with time zone",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "PasswordResetCodeHash",
                table: "Users");

            migrationBuilder.DropColumn(
                name: "PasswordResetCodeExpiresAt",
                table: "Users");
        }
    }
}
