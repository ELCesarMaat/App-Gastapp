using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Gastapp_API.Migrations
{
    /// <inheritdoc />
    public partial class percentSave : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<decimal>(
                name: "PercentSave",
                table: "Users",
                type: "numeric",
                nullable: false,
                defaultValue: 0m);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "PercentSave",
                table: "Users");
        }
    }
}
